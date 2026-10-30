const { rateLimit } = require('express-rate-limit');

const authLimitHandler = (windowMs, methodLabel) => (req, res, _next, options) => {
  const resetTime = req.rateLimit ? req.rateLimit.resetTime : new Date(Date.now() + windowMs);
  const retryAfter = Math.max(1, Math.ceil((resetTime.getTime() - Date.now()) / 1000));
  res.setHeader('Retry-After', retryAfter.toString());
  res.setHeader('Access-Control-Expose-Headers', 'Retry-After');
  res.status(429).json({
    ok: false,
    message: `Demasiados intentos de ${methodLabel}. Intenta de nuevo en ${retryAfter} segundos`,
    retryAfter,
    method: methodLabel.includes('PIN') ? 'PIN' : 'PASSWORD'
  });
};

const extractIdentifier = (req) => {
  const body = req.body || {};
  const id = String(body.email || body.account || body.identifier || '').trim().toLowerCase();
  const clientIp = req.ip || (req.headers['x-forwarded-for'] ? String(req.headers['x-forwarded-for']).split(',')[0] : '') || req.socket.remoteAddress || 'ip';
  return id ? `${id}_${clientIp}` : clientIp;
};

const loginLimiter = rateLimit({
  windowMs: 5 * 60 * 1000,
  limit: 3,
  standardHeaders: true,
  legacyHeaders: true,
  skipSuccessfulRequests: true,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => `pass_${extractIdentifier(req)}`,
  handler: authLimitHandler(5 * 60 * 1000, 'contraseña')
});

const pinLimiter = rateLimit({
  windowMs: 5 * 60 * 1000,
  limit: 3,
  standardHeaders: true,
  legacyHeaders: true,
  skipSuccessfulRequests: true,
  validate: { keyGeneratorIpFallback: false },
  keyGenerator: (req) => `pin_${extractIdentifier(req)}`,
  handler: authLimitHandler(5 * 60 * 1000, 'PIN de inicio de sesión')
});

const loginMiddleware = (req, res, next) => {
  const body = req.body || {};
  if (body.pin && !body.password) {
    return pinLimiter(req, res, next);
  }
  return loginLimiter(req, res, next);
};

module.exports = {
  loginMiddleware,
  pinLimiter
};
