"""Isolated ATLAS backend. Standard library; no OpenAI secret or chat database in APK.
Run behind HTTPS. No chat text is persisted server-side. Quotas are atomic SQLite reservations.
"""
import argparse, hashlib, hmac, json, os, re, secrets, sqlite3, threading, time
from datetime import datetime, timezone
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ACTIONS = {'open_app','open_settings','open_camera','search_apps','set_theme','show_battery','open_calendar','brightness_settings','web_search'}
THEMES = {'cyberpunk_neon','dark_terminal','corporate_dystopia'}
INSTRUCTIONS = ('Você é ATLAS, assistente do NEON OS. Responda em português brasileiro, de forma clara. '
 'Você é uma integração da API, não uma sessão do app ChatGPT. Não afirme possuir memória ou acesso ao aparelho. '
 'Para ações do celular, proponha somente neon_action; o aplicativo valida e pede autorização. '
 'Nunca afirme ter executado uma ação. Não execute código, shell, URLs ou comandos arbitrários.')
TOOLS=[{'type':'function','name':'neon_action','description':'Propor uma ação local autorizável no NEON. Não executa no servidor.',
 'strict':True,'parameters':{'type':'object','properties':{'name':{'type':'string','enum':sorted(ACTIONS)},'value':{'type':'string'},'number':{'type':'integer','minimum':0,'maximum':100}},'required':['name','value','number'],'additionalProperties':False}}]

class ApiError(Exception):
    def __init__(self,status,message): self.status=status; self.message=message

def valid_action(a):
    return (type(a) is dict and set(a)=={'name','value','number'} and a['name'] in ACTIONS
      and type(a['value']) is str and len(a['value'])<=160 and type(a['number']) is int and 0<=a['number']<=100
      and (a['name']!='set_theme' or a['value'] in THEMES)
      and (a['name'] not in ('open_app','web_search') or bool(a['value'].strip())))

class Backend:
    def __init__(self,db=None,provider=None):
        self.db=db or os.environ.get('ATLAS_DB','/data/atlas.sqlite3')
        self.provider=provider or self.openai
        self._lock=threading.Lock()
        parent=os.path.dirname(self.db)
        if parent: os.makedirs(parent,exist_ok=True)
        with self.connect() as c:
            c.executescript('''CREATE TABLE IF NOT EXISTS users(name TEXT PRIMARY KEY,salt TEXT NOT NULL,digest TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions(digest TEXT PRIMARY KEY,user TEXT NOT NULL,expires INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS attempts(identity TEXT PRIMARY KEY,count INTEGER NOT NULL,start INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS usage(period TEXT PRIMARY KEY,requests INTEGER NOT NULL,tokens INTEGER NOT NULL,cost_micros INTEGER NOT NULL);''')
        try: os.chmod(self.db,0o600)
        except OSError: pass
    def connect(self):return sqlite3.connect(self.db,timeout=15)
    def add_user(self,name,password):
        if not re.fullmatch('[A-Za-z0-9_.-]{3,48}',name) or len(password)<14: raise ValueError('Usuário válido e senha de no mínimo 14 caracteres são necessários.')
        salt=secrets.token_hex(24);digest=hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),310000).hex()
        with self.connect() as c:
            c.execute('INSERT INTO users VALUES(?,?,?) ON CONFLICT(name) DO UPDATE SET salt=excluded.salt,digest=excluded.digest',(name,salt,digest));c.execute('DELETE FROM sessions WHERE user=?',(name,))
    def throttle_login(self,identity):
        identity=hashlib.sha256(identity.encode()).hexdigest();now=int(time.time())
        with self._lock,self.connect() as c:
            c.execute('BEGIN IMMEDIATE');c.execute('DELETE FROM attempts WHERE start<?',(now-3600,))
            row=c.execute('SELECT count,start FROM attempts WHERE identity=?',(identity,)).fetchone()
            if row and row[0]>=5 and now-row[1]<300:raise ApiError(429,'Muitas tentativas de login. Aguarde 5 minutos.')
            c.execute('INSERT INTO attempts VALUES(?,?,?) ON CONFLICT(identity) DO UPDATE SET count=excluded.count,start=excluded.start',(identity,(row[0]+1 if row and now-row[1]<300 else 1),(row[1] if row and now-row[1]<300 else now)))
    def login(self,body,ip):
        name=body.get('username','');password=body.get('password','')
        if not isinstance(name,str) or not isinstance(password,str) or len(name)>48 or len(password)>256:raise ApiError(400,'Credenciais inválidas.')
        self.throttle_login('ip:'+ip);self.throttle_login('user:'+name)
        with self.connect() as c:row=c.execute('SELECT salt,digest FROM users WHERE name=?',(name,)).fetchone()
        salt,digest=row or ('00'*24,'00'*32)
        actual=hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),310000).hex()
        if not row or not hmac.compare_digest(actual,digest):raise ApiError(401,'Usuário ou senha do backend inválidos.')
        token=secrets.token_urlsafe(36);expiry=int(time.time())+86400
        with self.connect() as c:
            c.execute('DELETE FROM sessions WHERE expires<?',(int(time.time()),));c.execute('INSERT INTO sessions VALUES(?,?,?)',(hashlib.sha256(token.encode()).hexdigest(),name,expiry))
        return {'token':token,'expires_at':expiry}
    def authenticate(self,token):
        if not isinstance(token,str) or not 30<=len(token)<=200:raise ApiError(401,'Faça login no backend.')
        with self.connect() as c:row=c.execute('SELECT user FROM sessions WHERE digest=? AND expires>?',(hashlib.sha256(token.encode()).hexdigest(),int(time.time()))).fetchone()
        if not row:raise ApiError(401,'Sessão expirada. Faça login novamente.')
        return row[0]
    def logout(self,token):
        with self.connect() as c:c.execute('DELETE FROM sessions WHERE digest=?',(hashlib.sha256(token.encode()).hexdigest(),))
        return {'ok':True}
    def reserve(self,user,input_bytes):
        # Reserve a conservative upper token bound, including tool/instruction framing.
        output=min(512,max(64,int(os.environ.get('MAX_OUTPUT_TOKENS','512'))))
        tokens=input_bytes+len(INSTRUCTIONS.encode())+len(json.dumps(TOOLS).encode())+4096+output
        input_price=Decimal(os.environ.get('INPUT_PRICE_CEILING_PER_MILLION','0'))
        output_price=Decimal(os.environ.get('OUTPUT_PRICE_CEILING_PER_MILLION','0'))
        if input_price<=0 or output_price<=0:raise ApiError(503,'Preços máximos e limites precisam ser configurados no backend antes de ativar a IA.')
        cost=int(Decimal(tokens)*max(input_price,output_price))+1 # USD micros: tokens * dollars/million
        now=datetime.now(timezone.utc);day=now.strftime('%Y-%m-%d');month=now.strftime('%Y-%m')
        periods=[('global:day:'+day,10,20000,100000),('global:month:'+month,100,200000,1000000),('user:'+user+':day:'+day,10,20000,100000)]
        with self._lock,self.connect() as c:
            c.execute('BEGIN IMMEDIATE')
            for period,maxreq,maxtokens,maxcost in periods:
                c.execute('INSERT OR IGNORE INTO usage VALUES(?,0,0,0)',(period,));row=c.execute('SELECT requests,tokens,cost_micros FROM usage WHERE period=?',(period,)).fetchone()
                if row[0]+1>maxreq or row[1]+tokens>maxtokens or row[2]+cost>maxcost:raise ApiError(429,'Limite de uso ou orçamento local atingido. Nenhuma chamada de IA foi feita.')
            for period,*_ in periods:c.execute('UPDATE usage SET requests=requests+1,tokens=tokens+?,cost_micros=cost_micros+? WHERE period=?',(tokens,cost,period))
        # Reservations deliberately are not refunded; timeouts/restarts cannot accidentally exceed quota.
        return output
    def chat(self,user,body):
        message=body.get('message');history=body.get('history',[])
        if not isinstance(message,str) or not message.strip() or len(message)>1500:raise ApiError(400,'Mensagem inválida; máximo de 1.500 caracteres.')
        if not isinstance(history,list) or len(history)>8:raise ApiError(400,'Histórico excede o limite.')
        inputs=[]
        for item in history:
            if not isinstance(item,dict) or item.get('role') not in ('user','assistant') or not isinstance(item.get('content'),str):raise ApiError(400,'Histórico inválido.')
            inputs.append({'role':item['role'],'content':item['content']})
        inputs.append({'role':'user','content':message})
        size=len(json.dumps(inputs,ensure_ascii=False).encode())
        if size>6000:raise ApiError(400,'Contexto muito longo. Apague o histórico ou inicie outra conversa.')
        if self.provider==self.openai and (not os.environ.get('OPENAI_API_KEY') or not os.environ.get('OPENAI_MODEL')):raise ApiError(503,'OpenAI não configurada no backend. Nenhuma resposta de IA foi gerada.')
        output=self.reserve(user,size)
        payload={'model':os.environ.get('OPENAI_MODEL','configured-by-owner'),'store':False,'max_output_tokens':output,'instructions':INSTRUCTIONS,'input':inputs,'tools':TOOLS,'parallel_tool_calls':False,'safety_identifier':hashlib.sha256(user.encode()).hexdigest()}
        result=self.provider(payload)
        text=[];actions=[]
        for item in result.get('output',[]):
            if item.get('type')=='message':
                for content in item.get('content',[]):
                    if content.get('type')=='output_text' and isinstance(content.get('text'),str):text.append(content['text'])
            if item.get('type')=='function_call' and item.get('name')=='neon_action':
                try:a=json.loads(item.get('arguments','{}'))
                except (ValueError,TypeError):continue
                if valid_action(a):actions.append(a)
        reply='\n'.join(text)[:8000]
        if not reply and not actions:raise ApiError(502,'A API não retornou uma resposta utilizável.')
        return {'reply':reply,'actions':actions[:3],'source':'openai_responses','truncated':result.get('status')=='incomplete'}
    @staticmethod
    def openai(payload):
        request=Request('https://api.openai.com/v1/responses',data=json.dumps(payload).encode(),headers={'Authorization':'Bearer '+os.environ['OPENAI_API_KEY'],'Content-Type':'application/json'},method='POST')
        try:
            with urlopen(request,timeout=24) as response:
                raw=response.read(262145)
                if len(raw)>262144:raise ApiError(502,'Resposta da API excede o limite.')
                return json.loads(raw)
        except HTTPError as e:
            if e.code==429:raise ApiError(429,'Limite da OpenAI atingido. Tente mais tarde.')
            raise ApiError(502,'A OpenAI recusou a solicitação. Confira chave, modelo e saldo no backend.')
        except (URLError,TimeoutError,ValueError):raise ApiError(502,'Não foi possível concluir a chamada da OpenAI.')

def handler_for(backend):
    class Handler(BaseHTTPRequestHandler):
        server_version='AtlasBackend'
        def log_message(self,*args):pass # Never log credentials, user text or bearer tokens.
        def do_GET(self):
            if self.path=='/health':self.send_json(200,{'service':'atlas','version':'0.2.0','history':'client_encrypted','realtime':False})
            else:self.send_json(404,{'error':'Rota inexistente.'})
        def do_POST(self):
            try:
                self.connection.settimeout(30)
                length=int(self.headers.get('Content-Length','0'))
                if not 1<=length<=16000:raise ApiError(413,'Requisição excede o limite.')
                body=json.loads(self.rfile.read(length))
                if not isinstance(body,dict):raise ApiError(400,'JSON inválido.')
                if self.path=='/v1/login':response=backend.login(body,self.client_address[0])
                else:
                    authorization=self.headers.get('Authorization','')
                    if not authorization.startswith('Bearer '):raise ApiError(401,'Faça login no backend.')
                    token=authorization[7:];user=backend.authenticate(token)
                    if self.path=='/v1/chat':response=backend.chat(user,body)
                    elif self.path=='/v1/logout':response=backend.logout(token)
                    elif self.path=='/v1/realtime':raise ApiError(501,'Realtime não ativado: exige configuração e controle de custo por sessão. Use texto, voz manual do Android ou ChatGPT oficial.')
                    else:raise ApiError(404,'Rota inexistente.')
                self.send_json(200,response)
            except ApiError as e:self.send_json(e.status,{'error':e.message})
            except (ValueError,UnicodeError):self.send_json(400,{'error':'Requisição inválida.'})
            except Exception:self.send_json(500,{'error':'Backend indisponível; dados privados omitidos.'})
        def send_json(self,status,body):
            data=json.dumps(body,ensure_ascii=False).encode();self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8');self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(data)
    return Handler

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--add-user');parser.add_argument('--port',type=int,default=8787);args=parser.parse_args()
    backend=Backend()
    if args.add_user:
        password=os.environ.get('ATLAS_NEW_PASSWORD')
        if not password:raise SystemExit('Defina ATLAS_NEW_PASSWORD por secret de ambiente; não na linha de comando.')
        backend.add_user(args.add_user,password);print('Conta criada ou atualizada; sessões antigas revogadas.');return
    ThreadingHTTPServer((os.environ.get('ATLAS_BIND','127.0.0.1'),args.port),handler_for(backend)).serve_forever()
if __name__=='__main__':main()
