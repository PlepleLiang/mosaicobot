import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { once } from 'node:events';
import { createModelTunnel } from './model-usb-tunnel.mjs';

async function request(server, payload) {
  const socket = net.createConnection({ host: '127.0.0.1', port: server.address().port });
  socket.setTimeout(2000, () => socket.destroy(new Error('Test timeout')));
  await once(socket, 'connect');
  socket.write(payload);
  const [data] = await once(socket, 'data');
  socket.destroy();
  return data.toString();
}
for (const target of ['example.com:443', 'models-proxy.stepfun-inc.com:80', '127.0.0.1:443', 'models-proxy.stepfun-inc.com.evil:443']) {
  test('rejects CONNECT ' + target, async t => {
    const server = createModelTunnel({ connect() { throw new Error('Must not dial rejected target'); } });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => server.shutdown());
    assert.match(await request(server, 'CONNECT ' + target + ' HTTP/1.1\r\nHost: ' + target + '\r\n\r\n'), /^HTTP\/1.1 403/);
  });
}
test('rejects plaintext HTTP requests', async t => {
  const server = createModelTunnel();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => server.shutdown());
  assert.match(await request(server, 'GET / HTTP/1.1\r\nHost: localhost\r\n\r\n'), /^HTTP\/1.1 405/);
});
test('allowed connection forwards opaque bytes without terminating TLS', async t => {
  const echo = net.createServer(socket => socket.pipe(socket));
  echo.listen(0, '127.0.0.1');
  await once(echo, 'listening');
  const server = createModelTunnel({ connect(options) {
    assert.deepEqual(options, { host: 'models-proxy.stepfun-inc.com', port: 443 });
    return net.createConnection({ host: '127.0.0.1', port: echo.address().port });
  } });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => { server.shutdown(); echo.close(); });
  const socket = net.createConnection({ host: '127.0.0.1', port: server.address().port });
  socket.setTimeout(2000, () => socket.destroy(new Error('Test timeout')));
  await once(socket, 'connect');
  socket.write('CONNECT models-proxy.stepfun-inc.com:443 HTTP/1.1\r\nHost: models-proxy.stepfun-inc.com:443\r\n\r\n');
  const [response] = await once(socket, 'data');
  assert.match(response.toString(), /^HTTP\/1.1 200/);
  const bytes = Buffer.from([0x16, 0x03, 0x01, 0, 7, 4, 3, 2, 1]);
  socket.write(bytes);
  const [received] = await once(socket, 'data');
  assert.deepEqual(received, bytes);
  socket.destroy();
});
