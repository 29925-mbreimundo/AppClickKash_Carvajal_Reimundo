const { activeUser } = require('../services/userService');
const { sha256 } = require('../utils/crypto');

function authenticateToken(req, res, next) {
  const match = /^Bearer (.+)$/.exec(
    req.headers.authorization || ''
  );

  const user = match && activeUser(match[1]);

  if (!user) {
    return res.status(401).json({
      ok: false,
      message: 'Sesión expirada, inicia sesión de nuevo'
    });
  }

  req.user = user;
  req.sessionTokenHash = sha256(match[1]);

  next();
}

module.exports = {
  authenticateToken
};
