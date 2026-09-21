export const environment = {
  production: true,
  apiUrl: '/api',
  // Site key de Cloudflare Turnstile para el login (ver /panel/login). Es pública por diseño (viaja
  // al navegador), no hace falta ocultarla. Reemplázala por la real al crear el sitio en Cloudflare
  // (dash.cloudflare.com -> Turnstile) y activa la verificación en el backend con TURNSTILE_ENABLED=true
  // + TURNSTILE_SECRET_KEY en el VPS (ver application.properties). Mientras esté así, el widget se
  // muestra pero el backend no lo exige (app.turnstile.enabled=false por defecto).
  turnstileSiteKey: '0x4AAAAAAE_SSqy8oMKKt4Sb',
};
