function errorHandler(err, req, res, next) {
  console.error(err);

  res.status(err.status || 500).json({
    ok: false,
    message: err.status === 400
      ? 'Petición mal formada'
      : 'Error interno del servidor'
  });
}

function cleanBody(req, res, next) {
  const safe = { ...req.body };
  delete safe.password;
  delete safe.pin;
  console.log(`${req.method} ${req.path}`, safe);
  next();
}

module.exports = {
  errorHandler,
  cleanBody
};
