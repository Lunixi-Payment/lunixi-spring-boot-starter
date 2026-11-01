#!/usr/bin/env python3
"""Real Boot/Tomcat/Spring Security/JDBC smoke; no valid payment POST and no gateway charge."""
import base64, html, http.cookiejar, json, os, re, secrets, socket, subprocess, tempfile, time
from pathlib import Path
from urllib.request import build_opener, HTTPCookieProcessor, HTTPRedirectHandler, Request
from urllib.parse import urlencode
from urllib.error import HTTPError, URLError

root = Path(__file__).resolve().parents[1]
dist = root / 'build/install/lunixi-spring-checkout-example'
if not (dist / 'bin/lunixi-spring-checkout-example').is_file():
    raise RuntimeError('Run Gradle installDist first')
checks = 0

def ensure(condition, label):
    global checks
    checks += 1
    if not condition:
        raise RuntimeError(label)

with tempfile.TemporaryDirectory(prefix='lunixi-spring-native-http-') as work:
    work = Path(work)
    key_source = work / 'FixtureKey.java'
    key_source.write_text('import com.lunixi.sdk.auth.Ed25519KeyPair; class FixtureKey { public static void main(String[] args) { System.out.print(Ed25519KeyPair.generate().privateKeyPem()); } }')
    private_key = subprocess.check_output(['java', '--class-path', str(dist / 'lib/*'), str(key_source)], text=True)
    sock = socket.socket(); sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]; sock.close()
    origin = f'http://127.0.0.1:{port}'
    password = secrets.token_urlsafe(24)
    env = dict(os.environ, SERVER_PORT=str(port), SERVER_ADDRESS='127.0.0.1', LUNIXI_SECURE_COOKIES='false', LUNIXI_INITIALIZE_DISPOSABLE='true', LUNIXI_ENVIRONMENT='TEST', LUNIXI_MERCHANT_ID='http-fixture', LUNIXI_KEY_ID='unregistered-http-fixture', LUNIXI_PRIVATE_KEY=private_key, LUNIXI_WEBHOOK_SECRET=secrets.token_urlsafe(32), LUNIXI_STORAGE_KEY=base64.b64encode(secrets.token_bytes(32)).decode(), LUNIXI_PUBLIC_BASE_URL='https://billing.example', LUNIXI_JDBC_URL='jdbc:h2:file:' + str(work / 'native') + ';DB_CLOSE_ON_EXIT=FALSE', LUNIXI_JDBC_USER='native_fixture', LUNIXI_JDBC_PASSWORD=secrets.token_urlsafe(24), DEMO_ALICE_PASSWORD=password, DEMO_BOB_PASSWORD=secrets.token_urlsafe(24))
    jar = http.cookiejar.CookieJar(); http = build_opener(HTTPCookieProcessor(jar))
    log = open(work / 'server.log', 'w')
    process = subprocess.Popen([str(dist / 'bin/lunixi-spring-checkout-example')], cwd=work, env=env, stdout=log, stderr=log)
    def request(path, params=None):
        data = None if params is None else urlencode(params).encode()
        try:
            with http.open(Request(origin + path, data=data), timeout=10) as response:
                return response.status, response.read().decode()
        except HTTPError as error:
            return error.code, error.read().decode()
    def token(body):
        match = re.search(r'name="_csrf"[^>]*value="([^"]+)"', body)
        ensure(match is not None, 'Native CSRF field missing')
        return html.unescape(match.group(1))
    try:
        for _ in range(150):
            try:
                status, body = request('/login')
                if status == 200:
                    break
            except (URLError, ConnectionError):
                if process.poll() is not None:
                    raise RuntimeError('Native Spring application exited before startup')
            time.sleep(.1)
        else:
            raise RuntimeError('Native server did not start')
        ensure(status == 200, 'Native login GET failed')
        before = {cookie.name: cookie.value for cookie in jar}
        status, _ = request('/login', {'username': 'alice', 'password': password})
        ensure(status == 403, 'Missing login CSRF was accepted')
        status, body = request('/login')
        csrf = token(body)
        status, body = request('/login', {'_csrf': csrf, 'username': 'alice', 'password': password})
        ensure(status == 200 and 'Your orders' in body, 'Native JDBC login failed: status=' + str(status) + ', login_error=' + str('Sign-in failed' in body) + ', cookie_secure=' + str([cookie.secure for cookie in jar]) + ', page_heading=' + str(re.findall(r'<h1[^>]*>(.*?)</h1>', body)))
        after = {cookie.name: cookie.value for cookie in jar}
        ensure(any(after.get(key) != value for key, value in before.items()), 'Native session ID did not rotate')
        class NoRedirect(HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        anonymous = build_opener(NoRedirect())
        try:
            anonymous.open(Request(origin + '/payments/1/return', data=urlencode({'status': 'CAPTURED', 'paymentId': 'forged'}).encode(), headers={'Origin': 'https://pay.lunixi.com', 'Sec-Fetch-Site': 'cross-site', 'Content-Type': 'application/x-www-form-urlencoded'}), timeout=10)
            raise RuntimeError('Expected stateless callback 303')
        except HTTPError as callback:
            ensure(callback.code == 303 and callback.headers.get('Location') == '/payments/1/return', 'Anonymous callback did not use fixed same-origin 303')
            ensure(callback.read() == b'' and not callback.headers.get('Set-Cookie'), 'Anonymous callback exposed order data or created a session')
        try:
            anonymous.open(origin + '/payments/1/return', timeout=10)
            raise RuntimeError('Anonymous GET exposed order')
        except HTTPError as denied:
            ensure(denied.code == 302, 'Owned GET no longer requires native session')
        status, body = request('/payments/1/return')
        ensure(status == 200 and 'Pending' in body, 'Original native session did not resume on callback GET')
        status, _ = request('/payments/2/return')
        ensure(status == 404, 'Other-owner order was exposed')
        status, _ = request('/payments/1', {})
        ensure(status == 403, 'Payment CSRF guard failed')
        status, _ = request('/payments/1')
        ensure(status == 405, 'GET could start a payment')
        status, body = request('/payments/1/return?' + urlencode({'status': 'CAPTURED', 'amount': '1990', 'paymentId': 'forged'}))
        ensure(status == 200 and 'Pending' in body, 'Browser return changed native status')
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait()
        log.close()
    probe = work / 'Probe.java'
    probe.write_text('import java.sql.*; class Probe { public static void main(String[] args) throws Exception { try(Connection c=DriverManager.getConnection(System.getenv("LUNIXI_JDBC_URL"),System.getenv("LUNIXI_JDBC_USER"),System.getenv("LUNIXI_JDBC_PASSWORD")); Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM lunixi_attempts")) { r.next(); System.out.print(r.getInt(1)); } } }')
    count = subprocess.check_output(['java', '--class-path', str(dist / 'lib/*'), str(probe)], env=env, text=True)
    ensure(count.strip() == '0', 'A rejected browser request reserved a gateway payment')
    print(f'Spring Boot: real native HTTP/JDBC smoke passed ({checks} checks); no gateway payment request sent')
