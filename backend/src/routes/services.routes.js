const express = require('express');
const { q, tx } = require('../database/db');
const { validAmount, ref, round } = require('../utils/crypto');
const { authenticateToken } = require('../middleware/auth');

const router = express.Router();

const fail = (res, code, message) => res.status(code).json({ ok: false, message });

router.post('/pay', authenticateToken, (req, res) => {
  const { contract, amount } = req.body;

  if (!contract || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');

  const match = /^(LUZ|AGUA|NET|OTR)-\d{3,15}$/i.exec(String(contract).trim());

  if (!match) return fail(res, 404, 'Código de contrato no válido (ej. LUZ-123456)');
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');

  const balance = round(req.user.balance - amount);
  const reference = ref('SRV');

  const names = {
    LUZ: 'Luz',
    AGUA: 'Agua',
    NET: 'Internet / Cable',
    OTR: 'Otros servicios'
  };

  tx(() => {
    q.setBalance.run(balance, req.user.id);
    q.insertMovement.run(req.user.id, 'SERVICE', amount, String(contract).toUpperCase(), reference, Date.now());
  });

  res.json({
    ok: true,
    message: 'Servicio pagado',
    balance,
    service: names[match[1].toUpperCase()],
    reference
  });
});

module.exports = router;
