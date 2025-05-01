package lightorbdetect;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.defect.LightningOrbEvokeAction;
import com.megacrit.cardcrawl.actions.defect.LightningOrbPassiveAction;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.powers.FocusPower;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

@SpireInitializer
public class LightOrbDetect implements PostInitializeSubscriber {
    public LightOrbDetect() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        new LightOrbDetect();
    }

    @Override
    public void receivePostInitialize() {
        System.out.println("[LightOrbDetect] 模组成功加载");
    }

    /* ------------------ Patch PassiveAction ------------------ */
    @SpirePatch(clz = LightningOrbPassiveAction.class, method = "update")
    public static class PassiveActionPatch {
        public static void Postfix(LightningOrbPassiveAction __instance) {
            try {
                // 反射获取 private fields
                Field infoField = LightningOrbPassiveAction.class.getDeclaredField("info");
                infoField.setAccessible(true);
                DamageInfo info = (DamageInfo) infoField.get(__instance);

                Field hitAllField = LightningOrbPassiveAction.class.getDeclaredField("hitAll");
                hitAllField.setAccessible(true);
                boolean hitAll = (boolean) hitAllField.get(__instance);

                int damageBase   = info.base;
                int damageOutput = info.output;
                int focus        = getFocus();
                int enemyNum     = countAliveEnemies();

                System.out.printf("[LightOrbDetect] PassiveAction → base=%d, output=%d, hitAll=%b, focus=%d, enemies=%d%n",
                                  damageBase, damageOutput, hitAll, focus, enemyNum);

                sendEvent(damageBase, damageOutput, hitAll, focus, enemyNum);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* ------------------ Patch EvokeAction ------------------ */
    @SpirePatch(clz = LightningOrbEvokeAction.class, method = "update")
    public static class EvokeActionPatch {
        public static void Postfix(LightningOrbEvokeAction __instance) {
            try {
                // 反射获取 private fields
                Field infoField = LightningOrbEvokeAction.class.getDeclaredField("info");
                infoField.setAccessible(true);
                DamageInfo info = (DamageInfo) infoField.get(__instance);

                Field hitAllField = LightningOrbEvokeAction.class.getDeclaredField("hitAll");
                hitAllField.setAccessible(true);
                boolean hitAll = (boolean) hitAllField.get(__instance);

                int damageBase   = info.base;
                int damageOutput = info.output;
                int focus        = getFocus();
                int enemyNum     = countAliveEnemies();

                System.out.printf("[LightOrbDetect] EvokeAction → base=%d, output=%d, hitAll=%b, focus=%d, enemies=%d%n",
                                  damageBase, damageOutput, hitAll, focus, enemyNum);

                sendEvent(damageBase, damageOutput, hitAll, focus, enemyNum);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /* ------------------ Helpers ------------------ */
    private static int getFocus() {
        if (AbstractDungeon.player != null && AbstractDungeon.player.hasPower(FocusPower.POWER_ID)) {
            return AbstractDungeon.player.getPower(FocusPower.POWER_ID).amount;
        }
        return 0;
    }

    private static int countAliveEnemies() {
        int count = 0;
        for (AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
            if (!m.isDeadOrEscaped() && !m.halfDead) {
                count++;
            }
        }
        return count;
    }

    private static void sendEvent(int damageBase, int damageOutput, boolean hitAll, int focus, int enemyNum) {
        try {
            URL url = new URL("http://localhost:63333/event");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            String json = String.format(
                "{\"event\":\"LightningOrb\",\"damage_base\":%d,\"damage_output\":%d,\"hit_all\":%b,\"focus\":%d,\"enemy_num\":%d}",
                damageBase, damageOutput, hitAll, focus, enemyNum
            );

            System.out.println("[LightOrbDetect] 准备发送: " + json);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes());
                os.flush();
            }

            int code = conn.getResponseCode();
            System.out.println("[LightOrbDetect] 收到响应码: " + code);
            conn.disconnect();
        } catch (Exception e) {
            System.out.println("[LightOrbDetect] 发送失败：");
            e.printStackTrace();
        }
    }
}