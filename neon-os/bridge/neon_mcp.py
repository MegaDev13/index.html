"""MCP stdio bridge for bounded, read-only Termux diagnostics over SSH."""
import json
import os
import socket
import subprocess
import sys

COMMANDS = {
    "android": "getprop ro.build.version.release",
    "kernel": "uname -srm",
    "uptime": "uptime",
    "storage": 'df -h "$HOME"',
}
PROTOCOLS = {"2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"}
TOOL = {
    "name": "neon_diagnostic",
    "description": "Consulta somente leitura no NEON por USB ou no Termux por SSH.",
    "inputSchema": {
        "type": "object",
        "properties": {"check": {"type": "string", "enum": list(COMMANDS)}},
        "required": ["check"],
        "additionalProperties": False,
    },
    "annotations": {"readOnlyHint": True, "destructiveHint": False},
}


def launcher_diagnostic(check):
    token = os.environ.get("NEON_BRIDGE_TOKEN", "")
    if not token or len(token) > 64:
        raise ValueError("Missing session key")
    with socket.create_connection(("127.0.0.1", 38223), timeout=4) as connection:
        connection.sendall((json.dumps({"token": token, "check": check}) + "\n").encode())
        with connection.makefile("rb") as stream:
            raw = stream.readline(4097)
        if not raw.endswith(b"\n") or len(raw) > 4096:
            raise ValueError("Invalid response")
        response = json.loads(raw)
        if response.get("ok") is not True or not isinstance(response.get("value"), str):
            raise ValueError("Launcher rejected the query")
        return response["value"]


def diagnostic(name):
    if not isinstance(name, str) or name not in COMMANDS:
        return {"isError": True, "content": [{"type": "text", "text": "Diagnóstico fora da lista permitida."}]}
    if "--launcher" in sys.argv:
        try:
            return {"content": [{"type": "text", "text": launcher_diagnostic(name)}]}
        except (OSError, ValueError, AttributeError):
            return {"isError": True, "content": [{"type": "text", "text": "Sessão local indisponível. Confira o encaminhamento ADB e a chave temporária."}]}
    command = [
        "ssh", "-T", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
        "-o", "ConnectTimeout=4", "neon-bridge", COMMANDS[name],
    ]
    try:
        result = subprocess.run(command, capture_output=True, timeout=8, check=False)
        if result.returncode:
            message = "SSH indisponível. Confira o Termux, o encaminhamento ADB e a chave do aparelho."
        else:
            return {"content": [{"type": "text", "text": result.stdout[:8192].decode(errors="replace").strip()}]}
    except (OSError, subprocess.TimeoutExpired):
        message = "Não foi possível concluir a consulta SSH."
    return {"isError": True, "content": [{"type": "text", "text": message}]}


def reply(request):
    if not isinstance(request, dict) or request.get("jsonrpc") != "2.0":
        return {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "Invalid Request"}}
    if "id" not in request:
        return None
    answer = {"jsonrpc": "2.0", "id": request["id"]}
    method = request.get("method")
    params = request.get("params")
    if not isinstance(params, dict):
        params = {}
    if method == "initialize":
        version = params.get("protocolVersion")
        if not isinstance(version, str) or version not in PROTOCOLS:
            version = "2025-11-25"
        answer["result"] = {
            "protocolVersion": version,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "neon-bridge", "version": "0.3.3"},
        }
    elif method == "ping":
        answer["result"] = {}
    elif method == "tools/list":
        answer["result"] = {"tools": [TOOL]}
    elif method == "tools/call" and params.get("name") == TOOL["name"]:
        args = params.get("arguments")
        valid = isinstance(args, dict) and set(args) == {"check"}
        answer["result"] = diagnostic(args["check"] if valid else None)
    else:
        answer["error"] = {"code": -32601, "message": "Method not found"}
    return answer


def main():
    while raw := sys.stdin.buffer.readline(65537):
        if len(raw) > 65536:
            return
        try:
            answer = reply(json.loads(raw))
        except (UnicodeDecodeError, json.JSONDecodeError):
            answer = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "Parse error"}}
        if answer is not None:
            print(json.dumps(answer, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
