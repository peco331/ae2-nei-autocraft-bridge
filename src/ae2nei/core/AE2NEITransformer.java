package ae2nei.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class AE2NEITransformer implements IClassTransformer {

    private static final String TARGET_AUTO_CRAFTING = "codechicken.nei.recipe.AutoCraftingManager";
    private static final String TARGET_AUTO_CRAFTING_TASK = "codechicken.nei.recipe.AutoCraftingManager$1";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (TARGET_AUTO_CRAFTING.equals(transformedName)) {
            return transformAutoCraftingManager(basicClass);
        } else if (TARGET_AUTO_CRAFTING_TASK.equals(transformedName)) {
            return transformAutoCraftingTask(basicClass);
        } else if ("codechicken.nei.api.ShortcutInputHandler".equals(transformedName)) {
            return transformShortcutInputHandler(basicClass);
        } else if ("appeng.container.slot.SlotCraftingTerm".equals(transformedName)) {
            ClassNode cn = new ClassNode();
            new ClassReader(basicClass).accept(cn, 0);
            int matches = 0;
            for (Object obj : cn.methods) {
                MethodNode mn = (MethodNode) obj;
                if ("capCraftingAttempts".equals(mn.name) && "(I)I".equals(mn.desc)) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn.getOpcode() == Opcodes.IRETURN) {
                            mn.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "ae2nei/bridge/ServerCrafting", "cap", "(I)I", false));
                            matches++;
                        }
                    }
                }
            }
            if (matches != 1) throw new IllegalStateException("Unsupported AE2: capCraftingAttempts hook count=" + matches);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS); cn.accept(cw);
            System.out.println("[AE2-NEI-Bridge] Transformed SlotCraftingTerm.capCraftingAttempts");
            return cw.toByteArray();
        }

        return basicClass;
    }

    private byte[] transformAutoCraftingManager(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean transformed = false;
        for (Object obj : cn.methods) {
            MethodNode mn = (MethodNode) obj;
            if ("getInventoryItems".equals(mn.name) &&
                (mn.access & Opcodes.ACC_STATIC) != 0 &&
                mn.desc.startsWith("(Lnet/minecraft/client/gui/inventory/GuiContainer;)") &&
                mn.desc.endsWith("Lcodechicken/nei/ItemStackAmount;")) {

                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.ARETURN) {
                        InsnList toInject = new InsnList();
                        // Stack currently has ItemStackAmount
                        toInject.add(new VarInsnNode(Opcodes.ALOAD, 0)); // GuiContainer param
                        toInject.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "ae2nei/bridge/AE2NEICraftingBridge",
                            "appendTerminalInventoryItems",
                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            false
                        ));
                        toInject.add(new TypeInsnNode(Opcodes.CHECKCAST, "codechicken/nei/ItemStackAmount"));
                        mn.instructions.insertBefore(insn, toInject);
                        transformed = true;
                    }
                }
            }
        }

        if (transformed) {
            System.out.println("[AE2-NEI-Bridge] Successfully transformed AutoCraftingManager.getInventoryItems");
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        throw new IllegalStateException("Unsupported NEI: getInventoryItems hook not found");
    }

    /** Shift+C raises satisfied root goals after createMasterRoot(); remember the goals as they were before that. */
    private byte[] transformShortcutInputHandler(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        int matches = 0;
        for (Object obj : cn.methods) {
            MethodNode mn = (MethodNode) obj;
            if (!"autocraftingIgnoreInventory".equals(mn.name) || !"(Lcodechicken/nei/recipe/chain/RecipeChainMath;)V".equals(mn.desc)) continue;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode minsn = (MethodInsnNode) insn;
                if (minsn.getOpcode() == Opcodes.INVOKEVIRTUAL && "codechicken/nei/recipe/chain/RecipeChainMath".equals(minsn.owner)
                    && "createMasterRoot".equals(minsn.name) && "()Lcodechicken/nei/recipe/Recipe$RecipeId;".equals(minsn.desc)) {
                    // Stack: [math] -> [math, math] -> [math, rootId] -> [rootId]
                    mn.instructions.insertBefore(minsn, new InsnNode(Opcodes.DUP));
                    InsnList after = new InsnList();
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ae2nei/bridge/AE2NEICraftingBridge",
                        "ignoreInventoryRoot", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false));
                    after.add(new TypeInsnNode(Opcodes.CHECKCAST, "codechicken/nei/recipe/Recipe$RecipeId"));
                    mn.instructions.insert(minsn, after);
                    matches++;
                }
            }
        }
        if (matches != 1) throw new IllegalStateException("Unsupported NEI: autocraftingIgnoreInventory hook count=" + matches);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        System.out.println("[AE2-NEI-Bridge] Transformed ShortcutInputHandler.autocraftingIgnoreInventory");
        return cw.toByteArray();
    }

    private byte[] transformAutoCraftingTask(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean transformedCanCraft = false;
        boolean transformedCraft = false;
        boolean transformedReference = false;
        int transformedCount = 0;

        for (Object obj : cn.methods) {
            MethodNode mn = (MethodNode) obj;
            if ("execute".equals(mn.name) && "()V".equals(mn.desc)) {
                mn.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "ae2nei/bridge/AE2NEICraftingBridge", "beginProcessing", "()V", false));

                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode && Long.valueOf(64L).equals(((LdcInsnNode) insn).cst)) {
                        AbstractInsnNode next = insn.getNext();
                        while (next != null && next.getOpcode() < 0) next = next.getNext();
                        if (next != null && next.getOpcode() == Opcodes.LSUB) {
                            mn.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "ae2nei/bridge/AE2NEICraftingBridge", "completedCrafts", "()J", false));
                            transformedCount++;
                            // set() detaches insn; resume at the replacement's successor.
                            insn = next;
                        }
                    }
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode minsn = (MethodInsnNode) insn;

                        if ("codechicken/nei/recipe/RecipeHandlerRef".equals(minsn.owner) && "of".equals(minsn.name)
                            && "(Lcodechicken/nei/recipe/Recipe$RecipeId;)Lcodechicken/nei/recipe/RecipeHandlerRef;".equals(minsn.desc)) {
                            mn.instructions.insertBefore(minsn, new VarInsnNode(Opcodes.ALOAD, 1));
                            minsn.owner = "ae2nei/bridge/AE2NEICraftingBridge";
                            minsn.name = "recipeReference";
                            minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
                            minsn.itf = false;
                            mn.instructions.insert(minsn, new TypeInsnNode(Opcodes.CHECKCAST, "codechicken/nei/recipe/RecipeHandlerRef"));
                            transformedReference = true;
                        }

                        // 1. Replace RecipeHandlerRef.canCraft(GuiContainer)Z
                        if ("codechicken/nei/recipe/RecipeHandlerRef".equals(minsn.owner) &&
                            "canCraft".equals(minsn.name) &&
                            minsn.desc.startsWith("(Lnet/minecraft/client/gui/inventory/GuiContainer;)") &&
                            minsn.desc.endsWith("Z")) {

                            // Stack before call: [RecipeHandlerRef, GuiContainer]
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = "ae2nei/bridge/AE2NEICraftingBridge";
                            minsn.name = "canCraft";
                            minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
                            minsn.itf = false;
                            transformedCanCraft = true;
                        }

                        // 2. Replace RecipeHandlerRef.craft(GuiContainer;I)Z
                        if ("codechicken/nei/recipe/RecipeHandlerRef".equals(minsn.owner) &&
                            "craft".equals(minsn.name) &&
                            minsn.desc.startsWith("(Lnet/minecraft/client/gui/inventory/GuiContainer;I)") &&
                            minsn.desc.endsWith("Z")) {

                            // Stack before call: [RecipeHandlerRef, GuiContainer, int]
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = "ae2nei/bridge/AE2NEICraftingBridge";
                            minsn.name = "craft";
                            minsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;I)Z";
                            minsn.itf = false;
                            transformedCraft = true;
                        }
                    }
                }
            }
        }

        if (transformedCanCraft && transformedCraft && transformedReference && transformedCount == 1) {
            System.out.println("[AE2-NEI-Bridge] Successfully transformed AutoCraftingManager$1 (canCraft=" + transformedCanCraft + ", craft=" + transformedCraft + ")");
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        throw new IllegalStateException("Unsupported NEI worker: canCraft=" + transformedCanCraft + ", craft=" + transformedCraft + ", reference=" + transformedReference + ", count=" + transformedCount);
    }
}
