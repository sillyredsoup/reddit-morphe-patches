package local.reddit;

import app.morphe.patcher.patch.*;
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.Label;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;
import java.io.InputStream;
import java.util.*;
import kotlin.Unit;

/** Opt-in vertical viewer for the currently loaded Reddit Home feed. */
public final class VerticalHomeFeedPatch {
    private static final String EXT = "Llocal/reddit/extension/VerticalHomeFeed;";
    private static BytecodePatch patch;
    private VerticalHomeFeedPatch() { }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static synchronized BytecodePatch getVerticalHomeFeedPatch() {
        if (patch != null) return patch;
        patch = PatchKt.bytecodePatch("Reddit - Vertical home feed",
            "Adds a Home button that opens loaded posts in a vertical swipe viewer.", false,
            builder -> {
                builder.compatibleWith(new Compatibility("com.reddit.frontpage", "Reddit", null,
                    ApkFileType.APKM, 0xFF4500, null,
                    Collections.singletonList(new AppTarget("2026.14.0", false, 28)), false));
                builder.extendWith(VerticalHomeFeedPatch::extensionStream);
                builder.execute(context -> {
                    if (!"com.reddit.frontpage".equals(context.getPackageMetadata().getPackageName())
                        || !"2026.14.0".equals(context.getPackageMetadata().getVersionName()))
                        throw new IllegalStateException("Requires Reddit 2026.14.0");

                    MutableMethod mapper = one(context.mutableClassDefBy(
                        "Lcom/reddit/feeds/impl/data/mapper/link/d;"), "a", "Ljava/lang/Object;", 3);
                    int list = mapper.getImplementation().getRegisterCount() - 3;
                    mapper.getImplementation().addInstruction(0, new BuilderInstruction3rc(
                        Opcode.INVOKE_STATIC_RANGE, list, 1, ref("rememberLinks",
                            Collections.singletonList("Ljava/util/List;"), "V")));

                    String home = "Lcom/reddit/feedslegacy/switcher/impl/homepager/compose/HomePagerScreen;";
                    hookView(context.mutableClassDefBy(home), "c4", "attachHome");
                    hookView(context.mutableClassDefBy(home), "l4", "detachHome");

                    String viewer = "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/PostDetailPagerScreen;";
                    hookScreen(context.mutableClassDefBy(viewer), "n5", "pagerAttached");
                    hookView(context.mutableClassDefBy(viewer), "c4", "pagerAttached");
                    hookView(context.mutableClassDefBy(viewer), "l4", "pagerDetached");

                    MutableMethod pager = one(context.mutableClassDefBy(
                        "Lcom/reddit/ui/compose/pager/h;"), "b", "V", 11);
                    MutableMethodImplementation pagerImpl = pager.getImplementation();
                    int orientation = pagerImpl.getRegisterCount() - 10;
                    pagerImpl.addInstruction(0, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
                        orientation, 1, ref("chooseOrientation",
                            Collections.singletonList("Ljava/lang/Object;"), "Ljava/lang/Object;")));
                    pagerImpl.addInstruction(1, new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, orientation));
                    pagerImpl.addInstruction(2, new BuilderInstruction21c(Opcode.CHECK_CAST, orientation,
                        new ImmutableTypeReference("Landroidx/compose/foundation/gestures/Orientation;")));

                    for (String name : Arrays.asList("c", "d")) {
                        MutableMethod page = one(context.mutableClassDefBy(
                            "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/q0;"),
                            name, "Ljava/lang/Object;", 2);
                        MutableMethodImplementation impl = page.getImplementation();
                        int self = impl.getRegisterCount() - 3;
                        Label original = impl.newLabelForIndex(0);
                        impl.addInstruction(0, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
                            self, 2, ref("page", Arrays.asList("Ljava/lang/Object;", "Ljava/lang/String;"),
                                "Ljava/lang/Object;")));
                        impl.addInstruction(1, new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
                        impl.addInstruction(2, new BuilderInstruction21t(Opcode.IF_EQZ, 0, original));
                        impl.addInstruction(3, new BuilderInstruction11x(Opcode.RETURN_OBJECT, 0));
                    }

                    // The improved initial-page path skips q0.c entirely. Keep this
                    // viewer on the standard provider, which consumes our snapshot.
                    for (String name : Arrays.asList("a", "b")) {
                        MutableMethod method = one(context.mutableClassDefBy(
                            "Lcom/reddit/frontpage/presentation/listing/linkpager/refactor/q0;"),
                            name, "Ljava/lang/Object;", 1);
                        MutableMethodImplementation impl = method.getImplementation();
                        List<Instruction> code = new ArrayList<>(impl.getInstructions());
                        int hits = 0;
                        for (int i = code.size() - 2; i >= 0; i--) {
                            Instruction ins = code.get(i);
                            if (!(ins instanceof ReferenceInstruction)
                                || !(((ReferenceInstruction) ins).getReference() instanceof MethodReference)
                                || !"booleanValue".equals(((MethodReference)
                                    ((ReferenceInstruction) ins).getReference()).getName())
                                || code.get(i + 1).getOpcode() != Opcode.MOVE_RESULT) continue;
                            int result = ((OneRegisterInstruction) code.get(i + 1)).getRegisterA();
                            int self = impl.getRegisterCount() - 2;
                            impl.addInstruction(i + 2, new BuilderInstruction35c(Opcode.INVOKE_STATIC,
                                2, self, result, 0, 0, 0,
                                ref("useImprovedProvider", Arrays.asList("Ljava/lang/Object;", "Z"), "Z")));
                            impl.addInstruction(i + 3, new BuilderInstruction11x(Opcode.MOVE_RESULT, result));
                            hits++;
                        }
                        if (hits != 1) throw new IllegalStateException("Provider flag changed: " + name);
                    }
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });
        return patch;
    }

    private static void hookScreen(app.morphe.patcher.util.proxy.mutableTypes.MutableClass type,
                                   String method, String hook) {
        MutableMethod target = one(type, method, "V", 0);
        int self = target.getImplementation().getRegisterCount() - 1;
        target.getImplementation().addInstruction(0, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
            self, 1, ref(hook, Collections.singletonList("Ljava/lang/Object;"), "V")));
    }

    private static void hookView(app.morphe.patcher.util.proxy.mutableTypes.MutableClass type,
                                 String method, String hook) {
        MutableMethod target = one(type, method, "V", 1);
        int self = target.getImplementation().getRegisterCount() - 2;
        int count = "detachHome".equals(hook) || "pagerAttached".equals(hook)
            || "pagerDetached".equals(hook) ? 1 : 2;
        target.getImplementation().addInstruction(0, new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,
            self, count, ref(hook, count == 1 ? Collections.singletonList("Ljava/lang/Object;")
                : Arrays.asList("Ljava/lang/Object;", "Landroid/view/View;"), "V")));
    }

    private static MutableMethod one(app.morphe.patcher.util.proxy.mutableTypes.MutableClass type,
                                     String name, String result, int args) {
        MutableMethod found = null;
        for (MutableMethod method : type.getMethods()) {
            if (!name.equals(method.getName()) || !result.equals(method.getReturnType())
                || method.getParameterTypes().size() != args || method.getImplementation() == null)
                continue;
            if (found != null) throw new IllegalStateException("Ambiguous " + type.getType() + name);
            found = method;
        }
        if (found == null) throw new IllegalStateException("Missing " + type.getType() + name);
        return found;
    }

    private static ImmutableMethodReference ref(String name, List<String> parameters, String result) {
        return new ImmutableMethodReference(EXT, name, parameters, result);
    }

    private static InputStream extensionStream() {
        InputStream stream = VerticalHomeFeedPatch.class.getClassLoader()
            .getResourceAsStream("extensions/vertical-home-feed.mpe");
        if (stream == null) throw new IllegalStateException("Missing vertical-home-feed.mpe");
        return stream;
    }
}
