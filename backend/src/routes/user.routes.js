const express = require('express');
const { db, q } = require('../database/db');
const { publicUser } = require('../services/userService');
const { authenticateToken } = require('../middleware/auth');

const router = express.Router();

router.get('/me', authenticateToken, (req, res) => {
  res.json({
    ok: true,
    balance: req.user.balance,
    user: publicUser(req.user)
  });
});

router.get('/movements', authenticateToken, (req, res) => {
  const rawMovements = db.prepare(`
    SELECT type, amount, counterpart, reference, created_at as timestamp
    FROM movements
    WHERE user_id = ?
    ORDER BY created_at DESC, id DESC
  `).all(req.user.id);

  const movements = rawMovements.map(m => {
    let cp = m.counterpart;
    if (cp && cp.startsWith('ACC-')) {
      const u = q.userByAccount.get(cp);
      if (u) cp = u.name;
    }
    return { ...m, counterpart: cp };
  });

  res.json({
    ok: true,
    movements
  });
});

module.exports = router;
