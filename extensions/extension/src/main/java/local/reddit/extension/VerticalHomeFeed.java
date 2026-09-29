package local.reddit.extension;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.jvm.functions.Function0;

/** Runtime half of the opt-in vertical Home viewer for Reddit 2026.14.0. */
public final class VerticalHomeFeed {
    private static final String MARKER = "__morphe_vertical_home__";
    private static final Map<String, Object> LINKS = new LinkedHashMap<String, Object>(512, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object> entry) {
            return size() > 1200;
        }
    };
    private static volatile List<Object> snapshot = Collections.emptyList();
    private static volatile boolean vertical;
    private static volatile Object navScreen;

    private VerticalHomeFeed() { }

    public static void rememberLinks(List<?> links) {
        if (links == null) return;
        for (Object link : links) rememberLink(link);
    }

    public static void rememberLink(Object link) {
        if (link == null) return;
        synchronized (LINKS) {
            try {
                String id = string(field(link, "kindWithId"));
                if (id != null) LINKS.put(id, link);
                String rawId = string(call(link, "getId"));
                if (rawId != null) LINKS.put(rawId, link);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
    }

    public static void initializeNav(Object screen) {
        navScreen = screen;
    }

    @SuppressWarnings("unchecked")
    public static void addButton(Object builder) {
        if (!(builder instanceof List<?>) || ((List<?>) builder).isEmpty()) return;
        try {
            List<Object> items = (List<Object>) builder;
            Object original = items.get(0);
            Object content = field(original, "b");
            Constructor<?> ctor = original.getClass().getConstructor(String.class, content.getClass());
            items.add(1, ctor.newInstance("Vertical", content));
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static Function0<?> wrapClick(Object descriptor, Function0<?> original) {
        try {
            if (!"Vertical".equals(field(descriptor, "a"))) return original;
            return () -> {
                Object nav = navScreen;
                Activity activity = activity(nav);
                if (activity != null) {
                    Object screen = call(nav, "getCurrentScreen");
                    if (isHome(screen)) open(activity, screen);
                    else Toast.makeText(activity, "Open Home feed first", Toast.LENGTH_SHORT).show();
                }
                return kotlinUnit();
            };
        } catch (ReflectiveOperationException | RuntimeException ignored) { return original; }
    }

    public static void pagerAttached(Object screen) {
        vertical = marked(screen);
    }

    public static void pagerDetached(Object screen) {
        if (marked(screen)) {
            vertical = false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object chooseOrientation(Object original) {
        if (!vertical) return original;
        return Enum.valueOf((Class<? extends Enum>) original.getClass(), "Vertical");
    }

    public static boolean useImprovedProvider(Object provider, boolean original) {
        try { return MARKER.equals(field(field(provider, "a"), "B")) ? false : original; }
        catch (ReflectiveOperationException | RuntimeException ignored) { return original; }
    }

    /** Null means the ordinary Reddit provider must run. */
    public static Object page(Object provider, String cursor) {
        try {
            Object params = field(provider, "a");
            if (!MARKER.equals(field(params, "B"))) return null;
            List<Object> all = snapshot;
            int start = 0;
            if (cursor != null) {
                try { start = cursor.startsWith("vh:") ? Integer.parseInt(cursor.substring(3))
                    : all.size(); }
                catch (NumberFormatException ignored) { start = all.size(); }
            }
            if (start < 0 || start > all.size()) start = all.size();
            int end = Math.min(start + 20, all.size());
            String after = end < all.size() ? "vh:" + end : null;
            Class<?> listing = Class.forName("com.reddit.domain.model.listing.Listing");
            return listing.getConstructor(List.class, String.class, String.class,
                String.class, String.class, boolean.class, List.class).newInstance(
                    new ArrayList<>(all.subList(start, end)), after, null, null, null,
                    false, Collections.emptyList());
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private static void open(Activity activity, Object screen) {
        if (!isHome(screen)) return;
        try {
            Object child = call(screen, "O5");
            Object model = call(child, "F1");
            Object pager = field(model, "x");
            Object state = call(call(pager, "getState"), "getValue");
            Object sections = field(state, "b");
            if (!(sections instanceof Iterable<?>))
                throw new IllegalStateException("Home sections unavailable");
            ArrayList<Object> posts = new ArrayList<>();
            int sectionCount = 0;
            String firstKey = null;
            int linkCount;
            synchronized (LINKS) {
                linkCount = LINKS.size();
                for (Object section : (Iterable<?>) sections) {
                    String id = string(call(section, "a"));
                    if (firstKey == null) firstKey = id;
                    sectionCount++;
                    if (id != null && id.startsWith("feed_post_section_"))
                        id = id.substring("feed_post_section_".length());
                    else if (id != null && id.startsWith("post_preview_"))
                        id = id.substring("post_preview_".length());
                    Object link = LINKS.get(id);
                    if (eligible(link) && !posts.contains(link)) posts.add(link);
                }
            }
            if (posts.isEmpty()) throw new IllegalStateException(
                sectionCount == 0 ? "Home is still loading" :
                    "No linked posts (sections " + sectionCount + ", cached " + linkCount
                        + ", first " + firstKey + ")");
            snapshot = Collections.unmodifiableList(posts);
            Bundle args = new Bundle();
            String firstId = string(call(posts.get(0), "getId"));
            if (firstId == null) firstId = string(call(posts.get(0), "getKindWithId"));
            args.putString("selectedLinkId", firstId);
            args.putString("listingType", "HOME");
            args.putString("feed_data_source", MARKER);
            Class<?> sort = Class.forName("com.reddit.listing.model.sort.SortType");
            @SuppressWarnings({"unchecked", "rawtypes"}) Object none = Enum.valueOf((Class) sort, "NONE");
            args.putSerializable("sort", (java.io.Serializable) none);
            Object session = Class.forName("com.reddit.domain.model.post.NavigationSession")
                .getConstructor().newInstance();
            args.putParcelable("navigationSession", (Parcelable) session);
            Class<?> detail = Class.forName(
                "com.reddit.frontpage.presentation.listing.linkpager.refactor.PostDetailPagerScreen");
            Object viewer = detail.getConstructor(Bundle.class).newInstance(args);
            Class<?> base = Class.forName("com.reddit.screen.BaseScreen");
            Class<?> navEntry = Class.forName("com.reddit.navstack.h1");
            Class.forName("com.reddit.screen.b0")
                .getMethod("q", Context.class, base, navEntry)
                .invoke(null, activity, viewer, null);
        } catch (ReflectiveOperationException | RuntimeException error) {
            String reason = error.getMessage();
            Toast.makeText(activity, "Vertical feed: " + (reason != null ? reason
                    : error.getClass().getSimpleName()),
                Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isHome(Object screen) {
        try {
            Object child = call(screen, "O5");
            Object model = call(child, "F1");
            return "HOME".equals(String.valueOf(field(model, "M")));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static boolean marked(Object screen) {
        try {
            Object args = field(screen, "f60223b");
            return args instanceof Bundle && MARKER.equals(((Bundle) args).getString("feed_data_source"));
        } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
    }

    private static Activity activity(Object screen) {
        try { return (Activity) call(screen, "H3"); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Object call(Object receiver, String method) {
        if (receiver == null) return null;
        try { return receiver.getClass().getMethod(method).invoke(receiver); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Object field(Object receiver, String name) throws ReflectiveOperationException {
        if (receiver == null) return null;
        Class<?> type = receiver.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static String string(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static boolean eligible(Object link) {
        return link != null && !Boolean.TRUE.equals(call(link, "getPromoted"))
            && !Boolean.TRUE.equals(call(link, "isBlankAd"));
    }

    private static Object kotlinUnit() {
        try {
            Class<?> type = Class.forName("kotlin.Unit");
            try { return type.getField("a").get(null); }
            catch (NoSuchFieldException missing) { return type.getField("INSTANCE").get(null); }
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
}
