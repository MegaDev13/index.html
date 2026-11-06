"""CI fixture only: runs the real auth/quota/HTTP code with an explicit fake Responses provider."""
import os,sys,tempfile
from http.server import ThreadingHTTPServer
sys.path.insert(0,os.path.dirname(os.path.dirname(__file__)))
from server import Backend,handler_for
os.environ['INPUT_PRICE_CEILING_PER_MILLION']='1'
os.environ['OUTPUT_PRICE_CEILING_PER_MILLION']='2'
def fixture(payload):
 return {'output':[{'type':'message','content':[{'type':'output_text','text':'FIXTURE DE INTEGRAÇÃO: conexão Android/backend verificada. Nenhuma chamada real à OpenAI.'}]}]}
tmp=tempfile.TemporaryDirectory();backend=Backend(tmp.name+'/fixture.db',fixture);backend.add_user('neon_ci','ci-fixture-password-only')
ThreadingHTTPServer(('0.0.0.0',8787),handler_for(backend)).serve_forever()
