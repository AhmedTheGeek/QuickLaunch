import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ICONS = os.environ.get("QL_ICONS", os.path.join(HERE, "icons"))
OUT = os.path.join(HERE, "html")
os.makedirs(OUT, exist_ok=True)

PKG = {"Chrome":"com.android.chrome","Gmail":"com.google.android.gm","Maps":"com.google.android.apps.maps","YouTube":"com.google.android.youtube",
"WhatsApp":"com.whatsapp","Spotify":"com.spotify.music","Instagram":"com.instagram.android","Calendar":"com.google.android.calendar",
"Photos":"com.google.android.apps.photos","Messages":"com.google.android.apps.messaging","Clock":"com.google.android.deskclock",
"Camera":"com.google.android.GoogleCamera","Keep":"com.google.android.keep","Slack":"com.Slack","Netflix":"com.netflix.mediaclient",
"Files":"com.google.android.apps.nbu.files","Calculator":"com.google.android.calculator","Contacts":"com.google.android.contacts",
"Phone":"com.google.android.dialer","Telegram":"org.telegram.messenger","Translate":"com.google.android.apps.translate",
"Drive":"com.google.android.apps.docs","Weather":"com.google.android.apps.weather","Reddit":"com.reddit.frontpage","Fit":"com.google.android.apps.fitness",
"Teams":"com.microsoft.teams","Meet":"com.google.android.apps.tachyon","Authenticator":"com.google.android.apps.authenticator2","X":"com.twitter.android",
"LinkedIn":"com.linkedin.android","Uber":"com.ubercab","Amazon":"com.amazon.mShop.android.shopping","Home":"com.google.android.apps.chromecast.app",
"Outlook":"com.microsoft.office.outlook","YouTube Music":"com.google.android.apps.youtube.music","Discord":"com.discord","Termux":"com.termux"}
def ic(name): return f"file://{ICONS}/{PKG[name]}.png"

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:100%;height:100%;overflow:hidden;background:#0C0C0F}
body{font-family:Manrope,system-ui,sans-serif;color:#F5F5F7;position:relative}
.glow{position:absolute;border-radius:50%;filter:blur(120px)}
.head{position:absolute;z-index:3}
.head h1{font-weight:800;line-height:0.98;letter-spacing:-0.025em}
.head p{font-weight:500;color:#9A9AA3;margin-top:.45em;letter-spacing:-0.01em}
.device{position:absolute;background:#0A0A0C;border:12px solid #2A2A2F;box-shadow:0 60px 140px rgba(0,0,0,.7),0 0 0 2px rgba(255,255,255,.06),inset 0 0 0 2px #050506;overflow:hidden}
.screen{position:absolute;inset:0;overflow:hidden;background:#101014}
.ms{font-family:'Material Symbols Rounded';font-weight:400;line-height:1;display:inline-block;font-variation-settings:'FILL' 1,'wght' 400,'GRAD' 0,'opsz' 24}
/* ---- home screen (Pixel-style launcher) ---- */
.home{position:absolute;inset:-40px;padding:40px;font-family:Roboto,sans-serif;color:#F2F2F7;background:
  radial-gradient(55% 45% at 18% 12%,#4A5F92 0,transparent 62%),
  radial-gradient(50% 45% at 88% 72%,#5B4586 0,transparent 62%),
  radial-gradient(45% 40% at 60% 35%,#2C5068 0,transparent 60%),
  linear-gradient(#1C1D25,#0F0F14)}
.home.blur{filter:blur(3px) brightness(.9) saturate(1)}
.status{position:absolute;top:0;left:0;right:0;height:28px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;font:500 14px/1 Roboto;color:#F2F2F7;z-index:2}
.status .ic{display:flex;gap:4px;align-items:center}
.glance{position:absolute;left:24px;top:44px}
.glance .d{font:500 22px/1.15 Roboto;letter-spacing:-.01em}
.glance .w{display:flex;align-items:center;gap:6px;font:400 15px Roboto;color:#D8DAE2;margin-top:6px}
.widget{position:absolute;border-radius:24px;background:rgba(24,26,32,.82);backdrop-filter:blur(6px);padding:14px 16px;box-shadow:0 8px 24px rgba(0,0,0,.25)}
.widget .t{display:flex;justify-content:space-between;align-items:center;font:500 13px Roboto;color:#B8BBC6;margin-bottom:10px}
.widget .ev{display:flex;align-items:center;gap:10px;font:400 14px Roboto;color:#F2F2F7;margin-bottom:8px}
.widget .ev i{width:10px;height:10px;border-radius:3px;display:inline-block;flex:none}
.widget .ev span.tm{color:#9DA1AD;min-width:44px}
.wx{display:flex;align-items:center;gap:12px}.wx .big{font:400 40px/1 Roboto}.wx .sm{font:400 13px Roboto;color:#B8BBC6;line-height:1.35}
.grid{position:absolute;left:0;right:0;display:grid;justify-items:center;row-gap:22px}
.app{display:grid;justify-items:center;gap:6px;width:64px}
.app img{width:52px;height:52px;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,.35)}
.app span{font:400 12px Roboto;color:#F2F2F7;text-shadow:0 1px 3px rgba(0,0,0,.6);white-space:nowrap}
.dock{position:absolute;left:0;right:0;display:flex;justify-content:space-evenly}
.dock img{width:52px;height:52px;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,.35)}
.pill{position:absolute;left:16px;right:16px;height:48px;border-radius:24px;background:#2C2E36;display:flex;align-items:center;padding:0 16px;gap:12px;box-shadow:0 2px 8px rgba(0,0,0,.3)}
.pill .g{font:700 22px 'Manrope';background:linear-gradient(90deg,#4285F4,#EA4335,#FBBC05,#34A853);-webkit-background-clip:text;color:transparent}
.pill .sp{flex:1}.pill .ms{font-size:22px;color:#C8CBD4}
.nav{position:absolute;left:0;right:0;display:grid;place-items:center}.nav i{width:110px;height:4px;border-radius:2px;background:#E8E8ED;display:block}
/* ---- Quick Launch card: res/values/dimens.xml + colors.xml ---- */
.card{position:absolute;left:16px;right:16px;top:48px;margin:0 auto;max-width:560px;background:rgba(28,28,30,.95);border:1px solid rgba(255,255,255,.10);border-radius:20px;box-shadow:0 18px 50px rgba(0,0,0,.6),0 2px 8px rgba(0,0,0,.35);overflow:hidden;font-family:Roboto,sans-serif;z-index:5}
.input{height:64px;display:flex;align-items:center;padding:0 20px;gap:14px;font-size:20px;color:#F2F2F7}
.input .ms{font-size:22px;color:#8E8E93}.input .hint{color:#636366}
.caret{display:inline-block;width:2px;height:24px;background:#F2F2F7;margin-left:1px;vertical-align:-4px}
.divider{height:1px;background:rgba(255,255,255,.08)}
.body{padding:6px 8px}
.row{height:56px;display:flex;align-items:center;padding:0 12px;gap:16px;border-radius:10px;color:#F2F2F7;font-size:16px}
.row.sel{background:rgba(255,255,255,.08)}
.row img{width:40px;height:40px;border-radius:50%;flex:none}
.row .label{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row .badge{font-size:12px;color:#8E8E93;margin-right:10px}
.key{height:22px;min-width:26px;padding:0 5px;border-radius:6px;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.06);display:inline-flex;align-items:center;justify-content:center;color:#8E8E93;font-size:11px;font-family:Roboto}
.key .ms{font-size:16px;color:#8E8E93}
.footer{display:flex;align-items:center;justify-content:flex-end;gap:6px;padding:10px 20px;background:rgba(255,255,255,.025);border-top:1px solid rgba(255,255,255,.06);font:12px Roboto;color:#636366}
.footer .gap{width:14px}
/* ---- soft keyboard ---- */
.kbd{position:absolute;left:0;right:0;bottom:0;background:#1B1B1F;padding:8px 3px 10px;font-family:Roboto;z-index:6}
.kbd .strip{height:40px;display:flex;align-items:center;justify-content:space-around;color:#D7D7DC;font-size:16px;margin-bottom:6px}
.kbd .strip span{padding:0 10px}.kbd .strip .mid{color:#fff;border-left:1px solid #3A3A40;border-right:1px solid #3A3A40}
.kbd .r{display:flex;justify-content:center;gap:5px;margin-bottom:9px}
.kbd .k{width:31px;height:44px;border-radius:6px;background:#3B3B42;color:#F2F2F7;font-size:21px;display:grid;place-items:center;box-shadow:0 1px 0 rgba(0,0,0,.5)}
.kbd .k.f{background:#2A2A30;color:#D0D0D6;font-size:14px}.kbd .k.w{width:48px}.kbd .k.sp{width:170px}.kbd .k.ret{width:48px;background:#3B82F6;color:#fff}
.kbd .k .ms{font-size:20px}
.kbd .nav{position:static;height:24px}
/* ---- underlying app for the overlay scene ---- */
.notes{position:absolute;inset:0;background:#131317;padding:56px 24px 0;font-family:Roboto;color:#E8E8ED}
.notes h2{font-size:24px;font-weight:500;margin-bottom:6px}.notes .meta{font-size:13px;color:#7A7A82;margin-bottom:22px}
.notes .ln{height:12px;border-radius:6px;background:#2A2A30;margin-bottom:14px}.notes .ln.s{width:62%}.notes .ln.m{width:80%}.notes .ln.xs{width:38%}
"""
HEAD = """<!doctype html><html><head><meta charset="utf-8">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;700;800&family=Roboto:wght@400;500&family=Material+Symbols+Rounded:opsz,wght,FILL@24,400,1&display=block">
<style>%s</style></head><body>"""

STATUS = ('<div class="status"><span>10:08</span><span class="ic"><span class="ms" style="font-size:16px">wifi</span>'
          '<span class="ms" style="font-size:16px">signal_cellular_alt</span><span class="ms" style="font-size:16px">battery_full</span></span></div>')

def home(w, h, cols, apps, dock, blur=True, tablet=False, grid_top=100, widget_top=470):
    cell = (w - 32) / cols
    glance = '<div class="glance"><div class="d">Saturday, Sep 13</div><div class="w"><span class="ms" style="font-size:18px;color:#FFD166">clear_day</span>72° · Sunny</div></div>'
    cal3 = ('<div class="ev"><i style="background:#7BAAF7"></i><span class="tm">10:30</span>Design review</div>'
            '<div class="ev"><i style="background:#33B679"></i><span class="tm">12:00</span>Lunch with Sara</div>'
            '<div class="ev"><i style="background:#F6BF26"></i><span class="tm">15:00</span>Sprint planning</div>')
    if tablet:
        wid = (f'<div class="widget" style="left:24px;top:{widget_top}px;width:{w*0.46:.0f}px"><div class="t"><span>Calendar</span><span>Today</span></div>{cal3}</div>'
               f'<div class="widget" style="left:{24+w*0.46+16:.0f}px;top:{widget_top}px;width:{w*0.3:.0f}px"><div class="t"><span>Weather</span><span>Lisbon</span></div>'
               '<div class="wx"><span class="ms" style="font-size:44px;color:#FFD166">clear_day</span><div><div class="big">72°</div><div class="sm">H 78° · L 61°<br>Sunny all day</div></div></div></div>')
    else:
        wid = (f'<div class="widget" style="left:16px;right:16px;top:{widget_top}px"><div class="t"><span>Calendar</span><span>Today</span></div>'
               '<div class="ev"><i style="background:#7BAAF7"></i><span class="tm">10:30</span>Design review</div>'
               '<div class="ev"><i style="background:#33B679"></i><span class="tm">12:00</span>Lunch with Sara</div></div>')
    grid = (f'<div class="grid" style="top:{grid_top}px;grid-template-columns:repeat({cols},{cell:.1f}px);padding:0 16px">'
            + "".join(f'<div class="app"><img src="{ic(a)}"><span>{a}</span></div>' for a in apps) + '</div>')
    dock_y = h - 150
    dk = f'<div class="dock" style="top:{dock_y}px;padding:0 {16 if not tablet else int(w*0.2)}px">' + "".join(f'<img src="{ic(a)}">' for a in dock) + '</div>'
    pill = (f'<div class="pill" style="top:{h-84}px;{"left:"+str(int(w*0.2))+"px;right:"+str(int(w*0.2))+"px" if tablet else ""}"><span class="g">G</span><span class="sp"></span>'
            '<span class="ms">mic</span><span class="ms">photo_camera</span></div>')
    nav = f'<div class="nav" style="top:{h-26}px"><i></i></div>'
    return f'<div class="home{" blur" if blur else ""}">{glance}{wid}{grid}{dk}{pill}{nav}</div>' + STATUS

def row(name, sel=False, badge=None):
    b = f'<span class="badge">{badge}</span>' if badge else ""
    k = '<span class="key"><span class="ms">keyboard_return</span></span>' if sel else ""
    return f'<div class="row{" sel" if sel else ""}"><img src="{ic(name)}"><span class="label">{name}</span>{b}{k}</div>'

def card(query, rows, footer=False, selected=0, badges=None):
    badges = badges or {}
    q = f'<span>{query}</span><span class="caret"></span>' if query else '<span class="hint">Search apps</span>'
    body = "".join(row(n, i == selected, badges.get(n)) for i, n in enumerate(rows))
    foot = ""
    if footer:
        foot = ('<div class="footer"><span class="key"><span class="ms">keyboard_return</span></span> open<span class="gap"></span>'
                '<span class="key"><span class="ms">arrow_upward</span></span><span style="width:3px"></span><span class="key"><span class="ms">arrow_downward</span></span> navigate<span class="gap"></span>'
                '<span class="key">esc</span> close</div>')
    return f'<div class="card"><div class="input"><span class="ms">search</span>{q}</div><div class="divider"></div><div class="body">{body}</div>{foot}</div>'

def keyboard(word="", s1="", s2=""):
    strip = (f'<div class="strip"><span>{word}</span><span class="mid">{s1}</span><span>{s2}</span></div>' if word
             else '<div class="strip">' + "".join(f'<span class="ms" style="font-size:22px;color:#B5B5BC">{n}</span>' for n in ["apps","gif_box","settings","content_paste","mic"]) + '</div>')
    r1 = "".join(f'<span class="k">{c}</span>' for c in "qwertyuiop"); r2 = "".join(f'<span class="k">{c}</span>' for c in "asdfghjkl")
    r3 = '<span class="k f w"><span class="ms">shift</span></span>' + "".join(f'<span class="k">{c}</span>' for c in "zxcvbnm") + '<span class="k f w"><span class="ms">backspace</span></span>'
    r4 = '<span class="k f w">?123</span><span class="k f">,</span><span class="k f"><span class="ms">mood</span></span><span class="k sp"></span><span class="k f">.</span><span class="k ret"><span class="ms">keyboard_return</span></span>'
    return f'<div class="kbd">{strip}<div class="r">{r1}</div><div class="r">{r2}</div><div class="r">{r3}</div><div class="r">{r4}</div><div class="nav"><i></i></div></div>'

def device(left, top, width, height, radius, dp_width, dp_height, screen_html):
    zoom = (width - 24) / dp_width
    return (f'<div class="device" style="left:{left}px;top:{top}px;width:{width}px;height:{height}px;border-radius:{radius}px">'
            f'<div class="screen" style="zoom:{zoom:.4f};height:{dp_height}px">{screen_html}</div></div>')

def headline(x, y, w, h1, sub, size, subsize):
    return f'<div class="head" style="left:{x}px;top:{y}px;width:{w}px"><h1 style="font-size:{size}px">{h1}</h1><p style="font-size:{subsize}px">{sub}</p></div>'

def page(W, H, glows, head, dev):
    g = "".join(f'<div class="glow" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px;background:{c};opacity:{o}"></div>' for x, y, w, h, c, o in glows)
    return HEAD % CSS + f'<div style="position:absolute;inset:0;width:{W}px;height:{H}px">{g}{head}{dev}</div></body></html>'

PHONE_APPS = ["Gmail","Maps","YouTube","Photos","Drive","WhatsApp","Instagram","Spotify","Netflix","Slack","Calendar","Keep","Clock","Files","Translate","Telegram","Reddit","X","Uber","Amazon"]
PHONE_DOCK = ["Phone","Messages","Chrome","Camera","Gmail"]
TAB_APPS = ["Gmail","Maps","YouTube","Photos","Drive","Calendar","Keep","Files","WhatsApp","Instagram","Spotify","Netflix","Slack","Teams","Meet","Translate","Telegram","Reddit","X","Uber","Amazon","Clock","Weather","Termux"]
TAB_DOCK = ["Phone","Messages","Chrome","Camera","Gmail","Calendar"]

SCENES = {}
PH = dict(left=90, top=640, width=900, height=2000, radius=96, dp_width=360, dp_height=760)
GP = [(-200, 300, 900, 900, "#FFB020", 0.2), (500, 1500, 900, 900, "#4C7DFF", 0.2)]
ph_home = home(360, 760, 5, PHONE_APPS[:15], PHONE_DOCK, grid_top=100, widget_top=470)

SCENES["phone/01_type.png"] = (1080, 2400, GP, headline(90, 170, 900, "Type two letters.<br>Press Enter.", "The app opens. Nothing to tap.", 96, 40),
    device(**PH, screen_html=ph_home + card("ca", ["Calendar", "Camera", "Calculator"]) + keyboard("ca", "calendar", "can")))
SCENES["phone/02_keys.png"] = (1080, 2400, GP, headline(90, 170, 900, "Hands stay<br>on the keys.", "Arrows move. Enter opens. Esc closes.", 96, 40),
    device(**PH, screen_html=ph_home + card("te", ["Telegram", "Teams", "Termux"], footer=True, selected=1)))
SCENES["phone/03_most_used.png"] = (1080, 2400, GP, headline(90, 170, 900, "Your most used,<br>before you type.", "Learns what you open. Works offline.", 96, 40),
    device(**PH, screen_html=ph_home + card("", ["WhatsApp", "Chrome", "Spotify", "Gmail", "Maps", "YouTube"]) + keyboard()))
notes = ('<div class="notes"><h2>Standup notes</h2><div class="meta">Edited 9:52 · 3 people</div>' + "".join(f'<div class="ln {c}"></div>' for c in ["", "m", "s", "", "xs", "m", "", "s", "m", "xs", "", "m", "s"]) + '</div>'
         '<div style="position:absolute;inset:0;background:rgba(0,0,0,.45)"></div>')
SCENES["phone/04_overlay.png"] = (1080, 2400, GP, headline(90, 170, 900, "Over whatever<br>you're doing.", "A small card. Gone the moment you launch.", 96, 40),
    device(**PH, screen_html=notes + STATUS + card("sp", ["Spotify"]) + keyboard("sp", "spotify", "speak")))
SCENES["phone/05_initials.png"] = (1080, 2400, GP, headline(90, 170, 900, "Initials work too.", "Type “yt” for YouTube, “gm” for Gmail. Work apps are badged.", 96, 40),
    device(**PH, screen_html=ph_home + card("yt", ["YouTube", "YouTube Music"], badges={"YouTube Music": "Work"}) + keyboard("yt", "youtube", "ytm")))

T7 = dict(left=156, top=560, width=1500, height=1900, radius=72, dp_width=560, dp_height=740)
SCENES["tablet7/01_fold.png"] = (1812, 2176, [(-300, 200, 1200, 1200, "#FFB020", 0.16), (1000, 1300, 1200, 1200, "#4C7DFF", 0.16)],
    headline(156, 150, 1500, "Made for foldables<br>and tablets.", "The card stays centred and readable on any screen. Physical keyboards get key hints.", 110, 44),
    device(**T7, screen_html=home(560, 740, 6, TAB_APPS[:18], TAB_DOCK, tablet=True, grid_top=100, widget_top=400) + card("ma", ["Maps", "Messages", "Meet"], footer=True)))

T10L = dict(left=1040, top=190, width=1700, height=1200, radius=64, dp_width=720, dp_height=500)
keycaps = ('<div style="position:absolute;left:150px;top:720px;display:flex;align-items:center;gap:26px;font-family:Manrope">'
           '<span style="padding:26px 44px;border-radius:26px;background:linear-gradient(#3A3A42,#232328);box-shadow:0 10px 0 #15151A,0 22px 40px rgba(0,0,0,.6);font-weight:800;font-size:56px">Ctrl</span>'
           '<span style="font-size:56px;color:#9A9AA3;font-weight:500">+</span>'
           '<span style="padding:26px 120px;border-radius:26px;background:linear-gradient(#3A3A42,#232328);box-shadow:0 10px 0 #15151A,0 22px 40px rgba(0,0,0,.6);font-weight:800;font-size:56px">Space</span></div>')
SCENES["tablet10/01_landscape.png"] = (2560, 1600, [(-300, -200, 1300, 1300, "#FFB020", 0.16), (1700, 800, 1300, 1300, "#4C7DFF", 0.16)],
    headline(150, 260, 820, "From any app.<br>One shortcut.", "Ctrl+Space opens Quick Launch anywhere a keyboard is attached.", 104, 42) + keycaps,
    device(**T10L, screen_html=home(720, 500, 8, TAB_APPS[:8], TAB_DOCK, tablet=True, grid_top=96, widget_top=205) + card("sl", ["Slack"], footer=True)))

T10P = dict(left=140, top=700, width=1320, height=2100, radius=72, dp_width=500, dp_height=800)
SCENES["tablet10/02_portrait.png"] = (1600, 2560, [(-300, 300, 1200, 1200, "#FFB020", 0.16), (800, 1500, 1200, 1200, "#4C7DFF", 0.16)],
    headline(140, 170, 1320, "Nothing to scroll.<br>Nothing to tap.", "Results update on every keystroke, ranked by how you actually use your apps.", 104, 42),
    device(**T10P, screen_html=home(500, 800, 6, TAB_APPS[:18], TAB_DOCK, tablet=True, grid_top=100, widget_top=400) + card("ph", ["Phone", "Photos"], footer=True)))

for rel, (W, H, glows, head, dev) in SCENES.items():
    fn = os.path.join(OUT, rel.replace("/", "__").replace(".png", ".html"))
    open(fn, "w").write(page(W, H, glows, head, dev))
    print(f"{fn}\t{rel}\t{W}\t{H}")
