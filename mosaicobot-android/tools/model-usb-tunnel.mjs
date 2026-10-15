import http from 'node:http';
import net from 'node:net';
import { pathToFileURL } from 'node:url';

// No API key here. Only CONNECT to this exact service is allowed; TLS stays on the phone.
const TARGET = 'models-proxy.stepfun-inc.com:443';
export function createModelTunnel({ connect = net.createConnection } = {}) {
  const sockets = new Set();
  const server = http.createServer({ maxHeaderSize: 4096 }, (_req, res) => {
    res.writeHead(405, { Connection: 'close' });
    res.end('HTTPS CONNECT only');
  });
  server.on('connection', socket => {
    sockets.add(socket);
    socket.on('error', () => {});
    socket.on('close', () => sockets.delete(socket));
    socket.setTimeout(370_000, () => socket.destroy());
  });
  server.on('connect', (req, client, head) => {
    if (req.url !== TARGET) {
      client.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    const remote = connect({ host: 'models-proxy.stepfun-inc.com', port: 443 });
    sockets.add(remote);
    let ready = false;
    remote.setTimeout(12_000, () => remote.destroy());
    remote.on('connect', () => {
      ready = true;
      remote.setTimeout(370_000, () => remote.destroy());
      client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head.length) remote.write(head);
      remote.pipe(client);
      client.pipe(remote);
    });
    remote.on('error', () => {
      if (!ready) client.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
      else client.destroy();
    });
    client.on('close', () => remote.destroy());
    remote.on('close', () => { sockets.delete(remote); client.destroy(); });
  });
  server.on('clientError', (_error, socket) => socket.destroy());
  server.shutdown = () => { for (const socket of sockets) socket.destroy(); server.close(); };
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.argv[2] || 18787);
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid local port');
  const server = createModelTunnel();
  server.on('error', () => { console.error('USB model tunnel failed; check the local port.'); process.exitCode = 1; });
  server.listen(port, '127.0.0.1', () => console.log('USB model CONNECT tunnel ready on 127.0.0.1:' + port));
  process.on('SIGINT', () => server.shutdown());
  process.on('SIGTERM', () => server.shutdown());
}
