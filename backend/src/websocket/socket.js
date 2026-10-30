const { WebSocketServer } = require('ws');
const { activeUser, SESSION_TTL_MS } = require('../services/userService');
const { sha256 } = require('../utils/crypto');
const { q } = require('../database/db');

const sockets = new Map();

function push(account, payload) {
  const accKey = String(account || '').trim().toUpperCase();
  const clients = sockets.get(accKey) || new Set();

  clients.forEach((ws) => {
    if (ws.readyState === 1) {
      ws.send(JSON.stringify(payload));
    }
  });
}

function initWebSocket(server) {
  const wss = new WebSocketServer({
    server,
    path: '/ws'
  });

  wss.on('connection', (ws, req) => {
    const authHeader = req.headers['authorization'] || '';
    const match = /^Bearer (.+)$/i.exec(authHeader);
    const token = match
      ? match[1]
      : (new URL(req.url, 'http://x').searchParams.get('token') || '');

    const user = activeUser(token);

    if (!user) {
      return ws.close(1008, 'no autorizado');
    }

    ws.sessionTokenHash = sha256(token);
    ws.userAccount = user.account;
    ws.userId = user.id;

    const accKey = String(user.account || '').trim().toUpperCase();

    if (!sockets.has(accKey)) {
      sockets.set(accKey, new Set());
    }

    sockets.get(accKey).add(ws);

    console.log(`WS conectado: ${user.account} (${accKey})`);

    ws.send(JSON.stringify({
      type: 'CONNECTED',
      account: user.account,
      balance: q.userById.get(user.id).balance
    }));

    ws.on('close', () => {
      sockets.get(accKey)?.delete(ws);
      console.log(`WS cerrado: ${user.account}`);
    });
  });

  return { wss, push };
}

module.exports = {
  initWebSocket,
  push,
  sockets
};
