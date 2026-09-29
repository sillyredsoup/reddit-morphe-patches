package local.reddit.extension;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.widget.Toast;
import java.lang.reflect.*;
import java.util.*;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function0;
import kotlin.Unit;

/** Runtime hooks for Reddit 2026.14.0. */
public final class RedditMode {
    private static final String PREFS = "local.reddit.mode";
    private static final String ENABLED = "nsfw_only";
    private static final String HAD_SHOW = "old_show";
    private static final String HAD_BLUR = "old_blur";
    private static volatile Object repository;
    private static volatile Object gameItem;
    private static volatile String gameLabel;
    private static volatile Activity activity;
    private static volatile Object navScreen;
    private static volatile boolean mode;

    private RedditMode() {}

    public static void setGamesLabel(Object screen, Resources resources) {
        navScreen = screen;
        try {
            Activity current = (Activity) screen.getClass().getMethod("H3").invoke(screen);
            if (current != null) {
                activity = current;
                mode = current.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(ENABLED, false);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        try {
            int id = resources.getIdentifier("label_games", "string", "com.reddit.frontpage");
            if (id != 0) gameLabel = resources.getString(id);
        } catch (RuntimeException ignored) { }
    }

    public static void rememberItem(Object item) {
        try {
            String label = (String) item.getClass().getField("a").get(item);
            if (label.equals(gameLabel) || (gameLabel == null && "Games".equals(label)))
                gameItem = item;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    @SuppressWarnings("unchecked")
    public static void addButton(Object builder) {
        Object original = gameItem;
        if (original == null || !(builder instanceof List<?>)) return;
        try {
            Object content = original.getClass().getField("b").get(original);
            Constructor<?> ctor = original.getClass().getConstructor(String.class, content.getClass());
            Object button = ctor.newInstance("NSFW", content);
            ((List<Object>) builder).add(button);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static void rememberRepository(Object value) {
        repository = value;
    }

    public static Function0<?> wrapClick(Object descriptor, Function0<?> original) {
        try {
            String label = (String) descriptor.getClass().getField("a").get(descriptor);
            if ("NSFW".equals(label)) return () -> {
                Object screen = navScreen;
                if (screen != null) toggle(screen);
                return Unit.INSTANCE;
            };
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return original;
    }

    public static boolean selected(Object descriptor, Function0<?> click, String label,
                                   boolean original) {
        try {
            if ("NSFW".equals(descriptor.getClass().getField("a").get(descriptor)))
                return mode;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return original;
    }

    public static List<?> filterListing(List<?> original) {
        if (original == null || original.isEmpty()) return original;
        boolean nsfw = mode;
        ArrayList<Object> kept = null;
        for (int i = 0; i < original.size(); i++) {
            Object item = original.get(i);
            Object link = findLink(item, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
            boolean drop = link != null &&
                (flag(link, "getIsDevPlatformCustomPost") ||
                    (nsfw && !flag(link, "getOver18")));
            if (drop) {
                if (kept == null) {
                    kept = new ArrayList<>(original.size());
                    kept.addAll(original.subList(0, i));
                }
            } else if (kept != null) kept.add(item);
        }
        return kept == null ? original : kept;
    }

    public static void toggle(Object screen) {
        Activity current = null;
        try {
            current = (Activity) screen.getClass().getMethod("H3").invoke(screen);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        if (current == null) return;
        activity = current;
        Object repo = repository;
        if (repo == null) {
            Toast.makeText(current, "NSFW settings are not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences prefs = current.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            if (!mode) {
                boolean oldShow = getter(repo, "i");
                boolean oldBlur = getter(repo, "e");
                prefs.edit().putBoolean(HAD_SHOW, oldShow).putBoolean(HAD_BLUR, oldBlur)
                    .putBoolean(ENABLED, true).apply();
                mode = true;
                set(repo, "y", true);
                set(repo, "q", false);
            } else {
                boolean oldShow = prefs.getBoolean(HAD_SHOW, false);
                boolean oldBlur = prefs.getBoolean(HAD_BLUR, true);
                mode = false;
                prefs.edit().putBoolean(ENABLED, false).apply();
                set(repo, "y", oldShow);
                set(repo, "q", oldBlur);
            }
            Toast.makeText(current, mode ? "NSFW mode on" : "NSFW mode off", Toast.LENGTH_SHORT).show();
            current.recreate();
        } catch (ReflectiveOperationException | RuntimeException error) {
            Toast.makeText(current, "Could not change NSFW settings", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean getter(Object repo, String name) throws ReflectiveOperationException {
        return (Boolean) repo.getClass().getMethod(name).invoke(repo);
    }

    private static void set(Object repo, String name, boolean value) throws ReflectiveOperationException {
        for (Method method : repo.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != 2
                || method.getParameterTypes()[0] != boolean.class) continue;
            Continuation<Object> completion = new Completion();
            Object continuation = name.equals("q")
                ? new LambdaCompletion(completion) : new ImplCompletion(completion);
            method.invoke(repo, value, continuation);
            return;
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean flag(Object value, String name) {
        try { return (Boolean) value.getClass().getMethod(name).invoke(value); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static Object findLink(Object value, int depth, Set<Object> seen) {
        if (value == null || depth > 4 || !seen.add(value)) return null;
        if ("com.reddit.domain.model.Link".equals(value.getClass().getName())) return value;
        if (value instanceof Iterable<?>) {
            for (Object child : (Iterable<?>) value) {
                Object found = findLink(child, depth + 1, seen);
                if (found != null) return found;
            }
            return null;
        }
        String name = value.getClass().getName();
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")) return null;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass())
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object found = findLink(field.get(value), depth + 1, seen);
                    if (found != null) return found;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        return null;
    }

    private static final class Completion implements Continuation<Object> {
        @Override public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
        @Override public void resumeWith(Object result) { }
    }

    private static final class ImplCompletion extends ContinuationImpl {
        ImplCompletion(Continuation<Object> completion) { super(completion); }
        @Override protected Object invokeSuspend(Object result) { return result; }
    }

    private static final class LambdaCompletion extends SuspendLambda {
        LambdaCompletion(Continuation<Object> completion) { super(0, completion); }
        @Override protected Object invokeSuspend(Object result) { return result; }
    }
}
