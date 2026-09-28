"""Isolated Nginx test upstream; never connects to the real gateway."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps({'path': self.path,
                           'authorized': self.headers.get('Authorization') == 'Bearer fixture',
                           'tenant': self.headers.get('tenant-id')}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length', '0')))
        self.do_GET()

    def log_message(self, *args):
        pass


HTTPServer(('0.0.0.0', 48080), Handler).serve_forever()
