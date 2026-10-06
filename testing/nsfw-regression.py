#!/usr/bin/env python3
"""Exercise screen ownership and delayed account state without network timing.

Android/Compose fixtures model only the calls needed here. The emulator checks
the real app separately. --baseline runs the same checks against Git HEAD.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--baseline', action='store_true')
args = parser.parse_args()
extension = 'extensions/extension/src/main/java/local/reddit/extension/RedditMode.java'
source = (subprocess.check_output(['git', 'show', f'HEAD:{extension}'], cwd=repo).decode()
          if args.baseline else (repo / extension).read_text())
fixtures = {
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 boolean getBoolean(String name, boolean fallback);
 Editor edit();
 interface Editor { Editor putBoolean(String name, boolean value); void apply(); }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {
 private boolean state;
 public android.content.res.Resources getResources() { return null; }
 public SharedPreferences getSharedPreferences(String name, int mode) {
  return new SharedPreferences() {
   public boolean getBoolean(String key, boolean fallback) { return state; }
   public Editor edit() { return new Editor() {
    public Editor putBoolean(String key, boolean value) { state = value; return this; }
    public void apply() {}
   }; }
  };
 }
}''',
    'android/app/Activity.java': '''package android.app;
public class Activity extends android.content.Context {}''',
    'android/widget/Toast.java': '''package android.widget;
public class Toast {
 public static final int LENGTH_SHORT=0, LENGTH_LONG=1;
 public static Toast makeText(android.content.Context c, CharSequence s, int length) {
  return new Toast();
 }
 public void show() {}
}''',
    'android/util/Log.java': '''package android.util;
public class Log { public static int w(String tag, String message) { return 0; } }''',
    'androidx/compose/runtime/o1.java': '''package androidx.compose.runtime;
public class o1 {
 private Object value;
 public o1(Object value) { this.value=value; }
 public Object getValue() { return value; }
 public void setValue(Object value) { this.value=value; }
}''',
    'androidx/compose/runtime/j.java': '''package androidx.compose.runtime;
public class j { public static o1 B(Object value) { return new o1(value); } }''',
    'com/reddit/launch/bottomnav/BottomNavScreen.java': '''package com.reddit.launch.bottomnav;
public class BottomNavScreen {
 public int activityLookups;
 public android.app.Activity activity;
 public android.app.Activity H3() { activityLookups++; return activity; }
 public Object getCurrentScreen() { return null; }
}''',
    'Regression.java': '''import com.reddit.launch.bottomnav.BottomNavScreen;
import local.reddit.extension.RedditMode;
import kotlin.jvm.functions.Function0;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.SuspendLambda;
public class Regression {
 public static class Descriptor { public String a="NSFW"; }
 public static class Click implements Function0<Object> {
  public final BottomNavScreen owner;
  Click(BottomNavScreen owner) { this.owner=owner; }
  public Object invoke() { throw new AssertionError("Original tab action ran"); }
 }
 public static class Repository {
  public boolean show, blur;
  public int showWrites;
  // Simulate the asynchronous account getter lagging behind the button.
  public boolean i() { return false; }
  public Object y(boolean value, ContinuationImpl continuation) {
   show=value; showWrites++; return null;
  }
  public Object q(boolean value, SuspendLambda continuation) { blur=value; return null; }
 }
 public static void main(String[] args) {
  if (args[0].equals("owner")) {
   BottomNavScreen owner=new BottomNavScreen(), other=new BottomNavScreen();
   RedditMode.initialize(owner, null);
   Function0<?> click=RedditMode.wrapClick(new Descriptor(), new Click(owner));
   RedditMode.initialize(other, null);
   owner.activityLookups=0; other.activityLookups=0;
   click.invoke();
   if (owner.activityLookups != 1 || other.activityLookups != 0)
    throw new AssertionError("Click used a later unrelated navigation screen");
  } else {
   BottomNavScreen owner=new BottomNavScreen(); owner.activity=new android.app.Activity();
   Repository repository=new Repository();
   RedditMode.initialize(owner, null); RedditMode.rememberRepository(repository);
   Function0<?> click=RedditMode.wrapClick(new Descriptor(), new Click(owner));
   click.invoke();
   if (!repository.show || repository.blur) throw new AssertionError("Enable failed");
   click.invoke();
   if (repository.show || !repository.blur || repository.showWrites != 2)
    throw new AssertionError("Second click repeated enable while account getter lagged");
  }
  System.out.println("PASS " + args[0]);
 }
}''',
    'local/reddit/extension/RedditMode.java': source,
}
with tempfile.TemporaryDirectory(prefix='reddit-nsfw-regression-') as tmp:
    root = Path(tmp)
    files = []
    for name, text in fixtures.items():
        file = root / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)
        files.append(str(file))
    files += [str(repo / 'build-support/reddit-api/wl3/a.java')]
    files += [str(p) for p in (repo / 'build-support/reddit-api/kotlin/coroutines/jvm/internal').glob('*.java')]
    jars = [repo / '.local-tools/morphe-desktop.jar', repo / '.local-tools/android.jar']
    cp = ':'.join(str(p) for p in jars)
    subprocess.run(['javac', '-cp', cp, '-d', str(root / 'classes'), *files], check=True)
    failures = 0
    for case in ('owner', 'state'):
        result = subprocess.run(['java', '-cp', f'{root / "classes"}:{cp}', 'Regression', case])
        failures += result.returncode != 0
    raise SystemExit(bool(failures))
