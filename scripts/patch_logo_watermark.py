from pathlib import Path

path = Path('app/src/main/java/com/rgapro1/ocaso/MainActivity.java')
s = path.read_text(encoding='utf-8')

helper = '''
    /** Adds the RgaPro logo as a visible brand mark and a subtle watermark. */
    private View withWatermark(View page) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_rgapro);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setAlpha(0.11f);
        logo.setClickable(false);
        logo.setFocusable(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(700), dp(700), Gravity.CENTER);
        frame.addView(logo, lp);
        return frame;
    }

    private LinearLayout brandHeader() {
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_rgapro);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setAlpha(1f);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));
        brand.addView(tv("RgaPro", 26, Color.WHITE, true), new LinearLayout.LayoutParams(-2, dp(46)));
        return brand;
    }
'''

if 'private LinearLayout brandHeader()' not in s:
    marker = '    @Override public void onCreate(Bundle b)'
    if marker not in s:
        raise SystemExit('No se encontró el punto de inserción de MainActivity')
    s = s.replace(marker, helper + '\n' + marker, 1)

old = 'top.addView(tv("RgaPro",26,Color.WHITE,true)); top.addView(tv("Panel principal · "+currentUser,15,Color.WHITE,false));'
new = 'top.addView(brandHeader(),new LinearLayout.LayoutParams(-1,dp(48))); top.addView(tv("Panel principal · "+currentUser,15,Color.WHITE,false));'
if old in s:
    s = s.replace(old, new, 1)

old_login = 'l.addView(tv("RgaPro",32,NAVY,true));'
new_login = 'ImageView accessLogo = new ImageView(this); accessLogo.setImageResource(R.drawable.ic_rgapro); accessLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE); l.addView(accessLogo,new LinearLayout.LayoutParams(dp(96),dp(96))); l.addView(tv("RgaPro",32,NAVY,true));'
s = s.replace(old_login, new_login, 2)

s = s.replace('setContentView(l);}', 'setContentView(withWatermark(l));}', 2)
s = s.replace('setContentView(root);\n    }', 'setContentView(withWatermark(root));\n    }', 1)

path.write_text(s, encoding='utf-8')
print('Logo RgaPro visible en cabecera, acceso y marca de agua; marca de agua ampliada a 700dp')
