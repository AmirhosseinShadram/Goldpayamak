# گلدپیامک (Goldpayamak)

اپ اندروید برای ثبت درخواست و ارسال مستقیم پیامک از سیم‌کارت خود گوشی، بدون پنل و API.

- `core/` منطق خالص (ساخت متن حلقه‌ای پیام‌ها، قیمت، تاریخ شمسی) + تست واحد: `./gradlew :core:test`
- `app/` اپ اندروید (Compose + Room). ساخت APK: `./gradlew :app:assembleRelease`
- `app/signing.jks` کلید امضای ثابت (رمز: `android`). عمداً در مخزن است تا هر نسخهٔ جدید روی نسخهٔ قبلی نصب شود و داده‌های محلی کاربر پاک نشود.

## فعال‌سازی با کد یک‌بارمصرف

هر کاربر یک کد ۱۲ رقمی می‌گیرد که شما می‌سازید. اپ بار اول کد را روی سرور اعتبارسنجی می‌کند؛ کد همان لحظه مصرف می‌شود و تا حذف برنامه دیگر کدی خواسته نمی‌شود.

**سرور** (`server/`، بدون وابستگی، Node 22.5+ یا Docker):
```
cd server
ADMIN_KEY=یک-کلید-طولانی-و-محرمانه PORT=8080 node server.js
```
- پنل ادمین: `http://آدرس-سرور:8080/admin` (ساخت کد، برچسب برای هر کاربر، ابطال، وضعیت مصرف)
- پشت HTTPS (مثلاً Caddy/nginx) بگذارید و `ADMIN_KEY` را فاش نکنید.
- فایل `codes.db` (یا `DB_PATH`) را پشتیبان بگیرید.

**ساخت APK با آدرس سرور:**
```
./gradlew :app:assembleRelease -PactivationUrl=https://your-server
```

## فعال‌سازی آفلاین

این نوع APK با کلید عمومی صدور لایسنس ساخته می‌شود. کلید خصوصی را در یک مسیر خارج از مخزن بسازید و یک نسخهٔ پشتیبان امن از آن نگه دارید. برای به‌روزرسانی برنامه از همان کلید عمومی استفاده کنید تا لایسنس‌های قبلی معتبر بمانند.

```bash
umask 077
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -aes-256-cbc -out ../goldpayamak-license-private.pem
openssl pkey -in ../goldpayamak-license-private.pem -pubout -outform DER | openssl base64 -A > ../goldpayamak-license-public.b64
./gradlew :app:assembleRelease -PlicensePublicKey="$(cat ../goldpayamak-license-public.b64)"
```

پس از دریافت کد درخواست مشتری و تأیید پرداخت، کد فعال‌سازی را روی کامپیوتر خودتان بسازید:

```bash
bash tools/issue-license.sh ../goldpayamak-license-private.pem 'REQUEST_CODE'
```

کاربر خروجی را در فیلد کد فعال‌سازی وارد می‌کند. کد برای همان شناسهٔ بسته و دستگاه معتبر است. برای تست کنار نسخهٔ موجود، به دستور بیلد `-PredesignPreview=true` اضافه کنید و با کد درخواست همان نسخه لایسنس صادر کنید. کلید عمومی مربوط به همین لایسنس‌هاست و با کلید امضای APK فرق دارد. لایسنس آفلاین دائمی است و ابطال از راه دور ندارد.
