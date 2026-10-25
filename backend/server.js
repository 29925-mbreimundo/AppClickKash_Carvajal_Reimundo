// Backend: REST + WebSocket + SQLite
// Requiere Node.js 22.13 o superior.

const express = require('express');
const http = require('http');
const { initWebSocket } = require('./src/websocket/socket');
const { cleanBody, errorHandler } = require('./src/middleware/errorHandler');
const { q } = require('./src/database/db');
const { SESSION_TTL_MS } = require('./src/services/userService');

const authRoutes = require('./src/routes/auth.routes');
const userRoutes = require('./src/routes/user.routes');
const walletRoutes = require('./src/routes/wallet.routes');
const servicesRoutes = require('./src/routes/services.routes');

const PORT = process.env.PORT || 3000;

const app = express();

// Configuración de proxy para Tailscale Funnel / proxies inversos HTTPS
app.set('trust proxy', 1);

app.use(express.json());
app.use(cleanBody);

// RUTAS
app.use('/api/auth', authRoutes);
app.use('/api', userRoutes);
app.use('/api', walletRoutes);
app.use('/api/services', servicesRoutes);

// MANEJO DE ERRORES
app.use(errorHandler);

// SERVIDOR HTTP + WEBSOCKET
const server = http.createServer(app);

const { wss } = initWebSocket(server);

// LIMPIEZA DE SESIONES EXPIRADAS
const sessionCleanup = setInterval(() => {
  q.deleteExpiredSessions.run(Date.now() - SESSION_TTL_MS);

  wss.clients.forEach((ws) => {
    if (
      !ws.sessionTokenHash ||
      !q.userByToken.get(ws.sessionTokenHash, Date.now() - SESSION_TTL_MS)
    ) {
      ws.close(1008, 'Sesión vencida');
    }
  });
}, 60 * 1000);

sessionCleanup.unref();

// INICIAR BACKEND
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Backend en http://0.0.0.0:${PORT} (SQLite: backend/wallet.db)`);
});
