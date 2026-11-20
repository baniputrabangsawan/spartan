import { Hono } from 'hono';
import { secureHeaders } from 'hono/secure-headers';

type Bindings = {
  DB: D1Database;
  PHOTOS: R2Bucket;
  APP_ENV: 'production' | 'staging';
};

const app = new Hono<{ Bindings: Bindings }>();

app.use('*', secureHeaders());
app.use('*', async (c, next) => {
  c.header('Cache-Control', 'no-store');
  await next();
});

// This is infrastructure readiness, not an unauthenticated catalog endpoint.
app.get('/v1/health', async (c) => {
  try {
    const [schema] = await Promise.all([
      c.env.DB.prepare(
        "SELECT value FROM app_metadata WHERE key = 'schema_version'",
      ).first<{ value: string }>(),
      c.env.PHOTOS.list({ prefix: '__health__/', limit: 1 }),
    ]);

    if (schema?.value !== '1') {
      return c.json({ error: { code: 'storage_not_ready', message: 'Penyimpanan belum siap.' } }, 503);
    }

    return c.json({ ok: true, schema: 1, environment: c.env.APP_ENV });
  } catch (error) {
    console.error('Storage readiness failed', error);
    return c.json({ error: { code: 'storage_unavailable', message: 'Penyimpanan tidak dapat dihubungi.' } }, 503);
  }
});

app.notFound((c) => c.json({ error: { code: 'not_found', message: 'Rute tidak ditemukan.' } }, 404));
app.onError((error, c) => {
  console.error('Request failed', error);
  return c.json({ error: { code: 'internal_error', message: 'Ada masalah pada server.' } }, 500);
});

export default app;
