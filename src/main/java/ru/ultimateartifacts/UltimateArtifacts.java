package ru.ultimateartifacts;

import org.bukkit.*;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

public final class UltimateArtifacts extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey artifactKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> souls = new HashMap<>();
    private final Map<UUID, Long> phoenixUsed = new HashMap<>();
    private final Map<UUID, Long> lastBindTrigger = new HashMap<>();

    private static final class WeaponMenu implements InventoryHolder {
        final String weaponId;
        final Inventory inventory;
        WeaponMenu(String weaponId, String title) {
            this.weaponId = weaponId;
            this.inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_PURPLE + title);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class BindMenu implements InventoryHolder {
        final String weaponId;
        final String skillId;
        final Inventory inventory;
        BindMenu(String weaponId, String skillId, String title) {
            this.weaponId = weaponId;
            this.skillId = skillId;
            this.inventory = Bukkit.createInventory(this, 27, ChatColor.DARK_AQUA + title);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private enum Bind {
        LEFT_CLICK, RIGHT_CLICK, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, SPRINT_LEFT_CLICK, SPRINT_RIGHT_CLICK, NONE
    }

    @Override public void onEnable() {
        saveDefaultConfig(); artifactKey = new NamespacedKey(this, "artifact");
        Objects.requireNonNull(getCommand("artifacts")).setExecutor(this);
        Objects.requireNonNull(getCommand("artifacts")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("UltimateArtifacts enabled. Resource pack is in the resourcepack folder.");
    }

    private static final String[] IDS = {"void_scythe","thunder_blade","blood_dagger","guardian_shield","phoenix_feather","rift_crystal","time_hourglass","berserker_heart","frost_rune","summoning_bell","void_mace","thunder_mace","void_bow","thunder_bow"};
    private static final String[] NAMES = {"Серп пустоты","Громовой клинок","Кинжал кровопийцы","Щит хранителя","Перо феникса","Кристалл разлома","Песочные часы времени","Сердце берсерка","Руна ледяной тюрьмы","Колокол призыва","Булава пустоты","Булава грома","Лук пустоты","Лук грома"};
    private static final Material[] BASES = {Material.NETHERITE_SWORD,Material.NETHERITE_SWORD,Material.NETHERITE_SWORD,Material.SHIELD,Material.FEATHER,Material.AMETHYST_SHARD,Material.CLOCK,Material.HEART_OF_THE_SEA,Material.BLUE_ICE,Material.BELL,Material.MACE,Material.MACE,Material.BOW,Material.BOW};

    private static final String[] SCYTHE_SKILLS = {"slash", "well", "dash", "ultimate"};
    private static final String[] VOID_MACE_SKILLS = {"crush", "singularity", "rift", "harvest", "annihilation"};
    private static final String[] THUNDER_MACE_SKILLS = {"chain", "leap", "storm"};
    private static final String[] VOID_BOW_SKILLS = {"singularity_arrow", "rain", "phase", "soul_piercer", "eclipse"};
    private static final String[] THUNDER_BOW_SKILLS = {"storm_arrow", "volley", "thunder_mark"};

    private ItemStack makeItem(int index) {
        ItemStack item = new ItemStack(BASES[index]); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + NAMES[index]);
        meta.setLore(List.of(ChatColor.DARK_PURPLE + "Артефакт Бездны", ChatColor.GRAY + abilityHint(index), ChatColor.DARK_GRAY + "Выдаётся только командой администратора"));
        meta.setUnbreakable(true); meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, IDS[index]);
        meta.setItemModel(new NamespacedKey("ultimateartifacts", IDS[index])); item.setItemMeta(meta); return item;
    }
    private String abilityHint(int i) { return switch(i) { case 0 -> "ПКМ: меню и настройка биндов"; case 1 -> "Цепная молния при ударе"; case 2 -> "Похищает часть здоровья при ударе"; case 3 -> "ПКМ: защитный купол"; case 4 -> "Однократное спасение от смерти"; case 5 -> "ПКМ: скачок сквозь разлом"; case 6 -> "ПКМ: замедление времени вокруг"; case 7 -> "ПКМ: ярость ценой здоровья"; case 8 -> "ПКМ: ледяная тюрьма"; case 9 -> "ПКМ: призыв каменного стража"; case 10 -> "5 способностей • ПКМ: настройка клавиш"; default -> "3 способности • ПКМ: настройка клавиш"; }; }
    private String id(ItemStack item) { if(item==null || !item.hasItemMeta()) return null; return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING); }
    private boolean has(Player p, String id) { for(ItemStack it:p.getInventory().getContents()) if(id.equals(id(it))) return true; return false; }
    private boolean ready(Player p, String key, int seconds) { long now=System.currentTimeMillis(), end=cooldowns.getOrDefault(UUID.nameUUIDFromBytes((p.getUniqueId()+key).getBytes()),0L); if(end>now){p.sendActionBar(ChatColor.DARK_PURPLE+"Перезарядка: "+((end-now+999)/1000)+"с"); return false;} cooldowns.put(UUID.nameUUIDFromBytes((p.getUniqueId()+key).getBytes()),now+seconds*1000L); return true; }
    private List<LivingEntity> targets(Location center,double radius,Player owner) { List<LivingEntity> out=new ArrayList<>(); for(Entity e:center.getWorld().getNearbyEntities(center,radius,radius,radius)) if(e instanceof LivingEntity l && l!=owner && !(l instanceof ArmorStand) && (owner.getWorld()!=l.getWorld() || !owner.equals(l))) out.add(l); return out; }
    private void particles(Location l,Particle p,int count,double spread,double speed) { l.getWorld().spawnParticle(p,l,count,spread,spread,spread,speed); }
    private void damage(LivingEntity e,Player p,double amount) { e.damage(amount,p); }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        String heldId = id(e.getItem());
        if (heldId == null) return;

        if (isConfigurableWeapon(heldId)) {
            if (triggerInteractBind(p, heldId, e.getAction())) {
                e.setCancelled(true);
                return;
            }
            boolean bow = heldId.endsWith("_bow");
            if (!bow && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                e.setCancelled(true);
                openWeaponMenu(p, heldId);
            }
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        switch (heldId) {
            case "guardian_shield" -> shield(p);
            case "rift_crystal" -> riftDash(p);
            case "time_hourglass" -> timeStop(p);
            case "berserker_heart" -> berserk(p);
            case "frost_rune" -> frostPrison(p);
            case "summoning_bell" -> summon(p);
        }
    }

    private boolean isConfigurableWeapon(String id) {
        return "void_scythe".equals(id) || "void_mace".equals(id) || "thunder_mace".equals(id) || "void_bow".equals(id) || "thunder_bow".equals(id);
    }

    private String[] skillsFor(String weaponId) {
        return switch (weaponId) {
            case "void_scythe" -> SCYTHE_SKILLS;
            case "void_mace" -> VOID_MACE_SKILLS;
            case "thunder_mace" -> THUNDER_MACE_SKILLS;
            case "void_bow" -> VOID_BOW_SKILLS;
            case "thunder_bow" -> THUNDER_BOW_SKILLS;
            default -> new String[0];
        };
    }

    private String weaponTitle(String weaponId) {
        return switch (weaponId) {
            case "void_scythe" -> "Серп пустоты";
            case "void_mace" -> "Булава пустоты";
            case "thunder_mace" -> "Булава грома";
            case "void_bow" -> "Лук пустоты";
            case "thunder_bow" -> "Лук грома";
            default -> "Оружие";
        };
    }

    private String skillName(String weaponId, String skillId) {
        return switch (weaponId + ":" + skillId) {
            case "void_scythe:slash" -> "Разрез пустоты";
            case "void_scythe:well" -> "Гравитационная яма";
            case "void_scythe:dash" -> "Теневой рывок";
            case "void_scythe:ultimate" -> "Абсолютная пустота";
            case "void_mace:crush" -> "Удар коллапса";
            case "void_mace:singularity" -> "Чёрная сингулярность";
            case "void_mace:rift" -> "Разломный прыжок";
            case "void_mace:harvest" -> "Поглощение душ";
            case "void_mace:annihilation" -> "Конец вселенной";
            case "thunder_mace:chain" -> "Цепь молний";
            case "thunder_mace:leap" -> "Громовой обвал";
            case "thunder_mace:storm" -> "Грозовой фронт";
            case "void_bow:singularity_arrow" -> "Стрела сингулярности";
            case "void_bow:rain" -> "Звёздный дождь";
            case "void_bow:phase" -> "Фазовый выстрел";
            case "void_bow:soul_piercer" -> "Пронзатель душ";
            case "void_bow:eclipse" -> "Затмение";
            case "thunder_bow:storm_arrow" -> "Грозовая стрела";
            case "thunder_bow:volley" -> "Электрический залп";
            case "thunder_bow:thunder_mark" -> "Метка грома";
            default -> skillId;
        };
    }

    private Bind defaultBind(String weaponId, String skillId) {
        return switch (weaponId + ":" + skillId) {
            case "void_scythe:slash", "void_mace:crush", "thunder_mace:chain" -> Bind.LEFT_CLICK;
            case "void_scythe:well", "void_mace:singularity", "thunder_mace:leap" -> Bind.SHIFT_RIGHT_CLICK;
            case "void_scythe:dash", "void_mace:annihilation", "thunder_mace:storm" -> Bind.SPRINT_LEFT_CLICK;
            case "void_scythe:ultimate", "void_mace:harvest" -> Bind.SHIFT_LEFT_CLICK;
            case "void_mace:rift", "void_bow:phase", "thunder_bow:volley" -> Bind.SPRINT_RIGHT_CLICK;
            case "void_bow:singularity_arrow", "thunder_bow:storm_arrow" -> Bind.LEFT_CLICK;
            case "void_bow:rain", "thunder_bow:thunder_mark" -> Bind.SHIFT_LEFT_CLICK;
            case "void_bow:soul_piercer" -> Bind.SHIFT_RIGHT_CLICK;
            case "void_bow:eclipse" -> Bind.SPRINT_LEFT_CLICK;
            default -> Bind.NONE;
        };
    }

    private Bind getBind(Player p, String weaponId, String skillId) {
        String path = "bindings." + p.getUniqueId() + "." + weaponId + "." + skillId;
        String raw = getConfig().getString(path);
        if (raw == null) return defaultBind(weaponId, skillId);
        try { return Bind.valueOf(raw); } catch (IllegalArgumentException ex) { return Bind.NONE; }
    }

    private void setBind(Player p, String weaponId, String skillId, Bind bind) {
        for (String other : skillsFor(weaponId)) {
            if (!other.equals(skillId) && getBind(p, weaponId, other) == bind && bind != Bind.NONE) {
                getConfig().set("bindings." + p.getUniqueId() + "." + weaponId + "." + other, Bind.NONE.name());
            }
        }
        getConfig().set("bindings." + p.getUniqueId() + "." + weaponId + "." + skillId, bind.name());
        saveConfig();
    }

    private String bindLabel(Bind bind) {
        return switch (bind) {
            case LEFT_CLICK -> "ЛКМ";
            case RIGHT_CLICK -> "ПКМ";
            case SHIFT_LEFT_CLICK -> "Shift + ЛКМ";
            case SHIFT_RIGHT_CLICK -> "Shift + ПКМ";
            case SPRINT_LEFT_CLICK -> "Спринт + ЛКМ";
            case SPRINT_RIGHT_CLICK -> "Спринт + ПКМ";
            case NONE -> "Не назначено";
        };
    }

    private void openWeaponMenu(Player p, String weaponId) {
        WeaponMenu menu = new WeaponMenu(weaponId, weaponTitle(weaponId) + " • способности");
        String[] skills = skillsFor(weaponId);
        int[] slots = {10, 12, 14, 16, 22};
        for (int i = 0; i < skills.length; i++) {
            String skill = skills[i];
            menu.inventory.setItem(slots[i], menuItem(Material.NETHER_STAR,
                    ChatColor.LIGHT_PURPLE + skillName(weaponId, skill),
                    "Текущий бинд: " + bindLabel(getBind(p, weaponId, skill)),
                    "Нажми, чтобы изменить клавишу"));
        }
        menu.inventory.setItem(26, menuItem(Material.BARRIER, "Закрыть"));
        p.openInventory(menu.inventory);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 0.8f);
    }

    private void openBindMenu(Player p, String weaponId, String skillId) {
        BindMenu menu = new BindMenu(weaponId, skillId, "Бинд: " + skillName(weaponId, skillId));
        Bind[] binds = {Bind.LEFT_CLICK, Bind.RIGHT_CLICK, Bind.SHIFT_LEFT_CLICK,
                Bind.SHIFT_RIGHT_CLICK, Bind.SPRINT_LEFT_CLICK, Bind.SPRINT_RIGHT_CLICK, Bind.NONE};
        Material[] icons = {Material.IRON_SWORD, Material.SHIELD, Material.IRON_AXE,
                Material.CROSSBOW, Material.FEATHER, Material.TRIDENT, Material.BARRIER};
        for (int i = 0; i < binds.length; i++) {
            Bind bind = binds[i];
            String current = getBind(p, weaponId, skillId) == bind ? "УСТАНОВЛЕНО" : "Нажми для выбора";
            menu.inventory.setItem(10 + i, menuItem(icons[i], bindLabel(bind), current));
        }
        menu.inventory.setItem(22, menuItem(Material.ARROW, "Назад", "Вернуться к способностям"));
        p.openInventory(menu.inventory);
    }

    private boolean triggerInteractBind(Player p, String weaponId, Action action) {
        Bind bind = null;
        boolean sneaking = p.isSneaking();
        boolean sprinting = p.isSprinting();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
            bind = sneaking ? Bind.SHIFT_LEFT_CLICK : (sprinting ? Bind.SPRINT_LEFT_CLICK : Bind.LEFT_CLICK);
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
            bind = sneaking ? Bind.SHIFT_RIGHT_CLICK : (sprinting ? Bind.SPRINT_RIGHT_CLICK : Bind.RIGHT_CLICK);
        return bind != null && activateBoundSkill(p, weaponId, bind);
    }

    private boolean activateBoundSkill(Player p, String weaponId, Bind bind) {
        for (String skill : skillsFor(weaponId)) {
            if (getBind(p, weaponId, skill) == bind) {
                long now = System.currentTimeMillis();
                long previous = lastBindTrigger.getOrDefault(p.getUniqueId(), 0L);
                if (now - previous > 150L) {
                    lastBindTrigger.put(p.getUniqueId(), now);
                    activateSkill(p, weaponId, skill);
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler public void onWeaponSwing(PlayerArmSwingEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        String weaponId = id(p.getInventory().getItemInMainHand());
        if (!isConfigurableWeapon(weaponId)) return;
        Bind bind = p.isSneaking() ? Bind.SHIFT_LEFT_CLICK : (p.isSprinting() ? Bind.SPRINT_LEFT_CLICK : Bind.LEFT_CLICK);
        activateBoundSkill(p, weaponId, bind);
    }

    private void activateSkill(Player p, String weaponId, String skillId) {
        switch (weaponId + ":" + skillId) {
            case "void_scythe:slash" -> voidSlash(p);
            case "void_scythe:well" -> gravityWell(p);
            case "void_scythe:dash" -> shadowDash(p);
            case "void_scythe:ultimate" -> absoluteVoid(p);
            case "void_mace:crush" -> voidMaceCrush(p);
            case "void_mace:singularity" -> voidMaceSingularity(p);
            case "void_mace:rift" -> voidMaceRift(p);
            case "void_mace:harvest" -> voidMaceHarvest(p);
            case "void_mace:annihilation" -> voidMaceAnnihilation(p);
            case "thunder_mace:chain" -> thunderMaceChain(p);
            case "thunder_mace:leap" -> thunderMaceLeap(p);
            case "thunder_mace:storm" -> thunderMaceStorm(p);
            case "void_bow:singularity_arrow" -> voidBowSingularity(p);
            case "void_bow:rain" -> voidBowRain(p);
            case "void_bow:phase" -> voidBowPhase(p);
            case "void_bow:soul_piercer" -> voidBowSoulPiercer(p);
            case "void_bow:eclipse" -> voidBowEclipse(p);
            case "thunder_bow:storm_arrow" -> thunderBowStormArrow(p);
            case "thunder_bow:volley" -> thunderBowVolley(p);
            case "thunder_bow:thunder_mark" -> thunderBowMark(p);
        }
    }

    private ItemStack menuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void onWeaponMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof WeaponMenu || e.getInventory().getHolder() instanceof BindMenu) e.setCancelled(true);
    }

    @EventHandler public void onWeaponMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof WeaponMenu menu) {
            e.setCancelled(true);
            if (e.getRawSlot() == 26) { p.closeInventory(); return; }
            String[] skills = skillsFor(menu.weaponId);
            int[] slots = {10, 12, 14, 16, 22};
            for (int i = 0; i < skills.length; i++) {
                if (e.getRawSlot() == slots[i]) {
                    openBindMenu(p, menu.weaponId, skills[i]);
                    return;
                }
            }
        } else if (e.getInventory().getHolder() instanceof BindMenu menu) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22) { openWeaponMenu(p, menu.weaponId); return; }
            if (e.getRawSlot() >= 10 && e.getRawSlot() <= 16) {
                Bind[] binds = {Bind.LEFT_CLICK, Bind.RIGHT_CLICK, Bind.SHIFT_LEFT_CLICK,
                        Bind.SHIFT_RIGHT_CLICK, Bind.SPRINT_LEFT_CLICK, Bind.SPRINT_RIGHT_CLICK, Bind.NONE};
                Bind selected = binds[e.getRawSlot() - 10];
                setBind(p, menu.weaponId, menu.skillId, selected);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Бинд для «" + skillName(menu.weaponId, menu.skillId)
                        + "» установлен: " + bindLabel(selected));
                openWeaponMenu(p, menu.weaponId);
            }
        }
    }

    private void voidSlash(Player p) { if(!ready(p,"slash",5))return; Location eye=p.getEyeLocation(); Vector dir=eye.getDirection().normalize(); World w=p.getWorld(); w.playSound(p.getLocation(),Sound.ENTITY_WITHER_SHOOT,1.2f,0.65f); w.playSound(p.getLocation(),Sound.ENTITY_PLAYER_ATTACK_SWEEP,1f,0.5f);
        new BukkitRunnable(){int step=0; public void run(){ if(step++>=12){cancel();return;} Location c=eye.clone().add(dir.clone().multiply(step*0.9)); w.spawnParticle(Particle.SOUL_FIRE_FLAME,c,35,0.8,0.45,0.8,0.02); w.spawnParticle(Particle.PORTAL,c,25,0.7,0.5,0.7,0.25); for(LivingEntity t:targets(c,1.45,p)){damage(t,p,config().getDouble("scythe.slash-damage",7.0)); Vector pull=p.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.65); pull.setY(0.18); t.setVelocity(pull);}}}.runTaskTimer(this,0L,1L);
    }
    private void gravityWell(Player p) { if(!ready(p,"well",18))return; Location c=p.getTargetBlockExact(18)==null?p.getLocation().add(p.getLocation().getDirection().multiply(8)):p.getTargetBlockExact(18).getLocation().add(0.5,1,0); p.getWorld().playSound(c,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,2f,0.5f); new BukkitRunnable(){int tick=0; public void run(){if(tick++>=80){p.getWorld().createExplosion(c,0f,false,false);cancel();return;} p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,c,30,2,1,2,0.1); p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,c,12,1.6,0.7,1.6,0.01); for(LivingEntity t:targets(c,5,p)){Vector v=c.toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.35);v.setY(0.12);t.setVelocity(v);if(tick%20==0)damage(t,p,2.0);}}}.runTaskTimer(this,0L,1L); }
    private void shadowDash(Player p) { if(!ready(p,"dash",10))return; Vector d=p.getLocation().getDirection().normalize(); Location start=p.getLocation(); p.setVelocity(d.multiply(1.8).setY(0.25)); p.getWorld().playSound(start,Sound.ENTITY_ENDERMAN_TELEPORT,1.3f,0.6f); new BukkitRunnable(){int n=0; public void run(){if(n++>8){cancel();return;} Location l=p.getLocation();particles(l,Particle.PORTAL,45,0.8,0.2); for(LivingEntity t:targets(l,1.5,p))damage(t,p,3.0);}}.runTaskTimer(this,0,1); }
    private void absoluteVoid(Player p) { if(!ready(p,"ultimate",75))return; Location c=p.getTargetBlockExact(24)==null?p.getLocation().add(p.getLocation().getDirection().multiply(12)):p.getTargetBlockExact(24).getLocation().add(0.5,1,0); World w=p.getWorld(); w.playSound(c,Sound.ENTITY_WARDEN_SONIC_BOOM,2f,0.55f); new BukkitRunnable(){int t=0; public void run(){if(t++>=45){for(LivingEntity e:targets(c,9,p)){damage(e,p,config().getDouble("scythe.ultimate-damage",24));e.setVelocity(c.toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.2).setY(0.5));}w.spawnParticle(Particle.EXPLOSION_EMITTER,c,2);w.spawnParticle(Particle.SOUL_FIRE_FLAME,c,250,5,3,5,0.08);w.playSound(c,Sound.ENTITY_GENERIC_EXPLODE,2f,0.7f);cancel();return;} double r=0.25+t*0.19; for(int i=0;i<24;i++){double a=i*Math.PI/12; Location q=c.clone().add(Math.cos(a)*r,Math.sin(t*0.15)*0.5,Math.sin(a)*r);w.spawnParticle(Particle.REVERSE_PORTAL,q,2,0,0,0,0.01);} if(t%10==0)w.spawnParticle(Particle.SONIC_BOOM,c,1);}}.runTaskTimer(this,0,1); }

    private Location aimedLocation(Player p, int distance) {
        org.bukkit.block.Block block = p.getTargetBlockExact(distance);
        return block == null ? p.getLocation().add(p.getLocation().getDirection().multiply(Math.min(7, distance))).add(0, 1, 0)
                : block.getLocation().add(0.5, 1, 0.5);
    }

    private void voidMaceCrush(Player p) {
        if (!ready(p, "void_mace_crush", 7)) return;
        Location center = aimedLocation(p, 12); World w = p.getWorld();
        w.playSound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.8f, 0.55f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        w.spawnParticle(Particle.REVERSE_PORTAL, center, 110, 2.2, 1.0, 2.2, 0.12);
        for (LivingEntity target : targets(center, 4.5, p)) {
            damage(target, p, 13.0);
            Vector pull = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.9);
            pull.setY(0.65); target.setVelocity(pull);
        }
    }

    private void voidMaceSingularity(Player p) {
        if (!ready(p, "void_mace_singularity", 24)) return;
        Location center = aimedLocation(p, 16); World w = p.getWorld();
        w.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.35f);
        new BukkitRunnable() { int tick = 0; public void run() {
            if (tick++ >= 100) {
                for (LivingEntity target : targets(center, 7, p)) { damage(target, p, 12); target.setVelocity(new Vector(0, 0.9, 0)); }
                w.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2); w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.45f); cancel(); return;
            }
            w.spawnParticle(Particle.REVERSE_PORTAL, center, 35, 2.5, 1.5, 2.5, 0.08);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 12, 1.8, 1, 1.8, 0.01);
            for (LivingEntity target : targets(center, 7, p)) {
                Vector v = center.toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.42);
                v.setY(0.12); target.setVelocity(v);
                if (tick % 20 == 0) damage(target, p, 2.5);
            }
        }}.runTaskTimer(this, 0L, 1L);
    }

    private void voidMaceRift(Player p) {
        if (!ready(p, "void_mace_rift", 13)) return;
        Location from = p.getLocation(); Vector direction = from.getDirection().normalize();
        Location destination = from.clone().add(direction.clone().multiply(13));
        while ((destination.getBlock().isSolid() || destination.clone().add(0, 1, 0).getBlock().isSolid()) && destination.distanceSquared(from) > 4) destination.subtract(direction.clone().multiply(0.5));
        if (destination.distanceSquared(from) <= 4) { p.sendActionBar(ChatColor.RED + "Путь заблокирован"); return; }
        p.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 90, 0.8, 1, 0.8, 0.35);
        p.teleport(destination); p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination.clone().add(0, 1, 0), 100, 1.2, 1, 1.2, 0.35);
        p.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.8f, 0.55f);
        for (LivingEntity target : targets(destination, 3.5, p)) { damage(target, p, 8); target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2)); }
    }

    private void voidMaceHarvest(Player p) {
        if (!ready(p, "void_mace_harvest", 17)) return;
        double healed = 0;
        for (LivingEntity target : targets(p.getLocation(), 7, p)) {
            damage(target, p, 8);
            healed += 1.5;
            Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.45);
            target.setVelocity(pull.setY(0.2));
            target.getWorld().spawnParticle(Particle.SOUL, target.getLocation().add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0.04);
        }
        if (healed > 0) p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + Math.min(8, healed)));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 0.7f);
    }

    private void voidMaceAnnihilation(Player p) {
        if (!ready(p, "void_mace_annihilation", 85)) return;
        Location center = aimedLocation(p, 22); World w = p.getWorld();
        w.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.7f, 0.55f);
        new BukkitRunnable() { int tick = 0; public void run() {
            if (tick++ >= 35) {
                for (LivingEntity target : targets(center, 10, p)) {
                    damage(target, p, 28);
                    Vector launch = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.35); launch.setY(0.85); target.setVelocity(launch);
                }
                w.spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 260, 5, 3, 5, 0.08);
                w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f); cancel(); return;
            }
            double radius = 0.3 + tick * 0.22;
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                Location point = center.clone().add(Math.cos(angle) * radius, Math.sin(tick * 0.17) * 0.7, Math.sin(angle) * radius);
                w.spawnParticle(Particle.REVERSE_PORTAL, point, 2, 0, 0, 0, 0.02);
            }
            if (tick % 7 == 0) w.spawnParticle(Particle.SONIC_BOOM, center, 1);
        }}.runTaskTimer(this, 0L, 1L);
    }

    private void thunderMaceChain(Player p) {
        if (!ready(p, "thunder_mace_chain", 8)) return;
        Location center = aimedLocation(p, 16); List<LivingEntity> hit = targets(center, 4, p);
        if (hit.isEmpty()) hit = targets(p.getLocation().add(p.getLocation().getDirection().multiply(5)), 3.5, p);
        int count = 0;
        for (LivingEntity target : hit) {
            if (count++ >= 5) break;
            p.getWorld().strikeLightningEffect(target.getLocation()); damage(target, p, 8);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 35, 1));
        }
        p.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.85f);
    }

    private void thunderMaceLeap(Player p) {
        if (!ready(p, "thunder_mace_leap", 14)) return;
        Vector direction = p.getLocation().getDirection().normalize();
        p.setVelocity(direction.multiply(1.15).setY(1.15));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.8f);
        new BukkitRunnable() { int ticks = 0; public void run() {
            if (!p.isOnline() || ticks++ >= 18 || (ticks > 3 && p.isOnGround())) {
                Location center = p.getLocation();
                p.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.1f);
                p.getWorld().strikeLightningEffect(center);
                for (LivingEntity target : targets(center, 5, p)) { damage(target, p, 10); target.setVelocity(new Vector(0, 0.8, 0)); }
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 100, 2, 0.5, 2, 0.2); cancel(); return;
            }
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 15, 0.4, 0.5, 0.4, 0.08);
        }}.runTaskTimer(this, 0L, 1L);
    }

    private void thunderMaceStorm(Player p) {
        if (!ready(p, "thunder_mace_storm", 32)) return;
        Location center = p.getLocation(); p.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
        new BukkitRunnable() { int ticks = 0; public void run() {
            if (ticks++ >= 100 || !p.isOnline()) { cancel(); return; }
            Location now = p.getLocation();
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, now, 28, 5, 2, 5, 0.12);
            if (ticks % 20 == 0) {
                List<LivingEntity> nearby = targets(now, 6, p);
                for (int i = 0; i < Math.min(4, nearby.size()); i++) { LivingEntity target = nearby.get(i); p.getWorld().strikeLightningEffect(target.getLocation()); damage(target, p, 4); }
            }
        }}.runTaskTimer(this, 0L, 2L);
    }

    private void voidBowSingularity(Player p) { if(!ready(p,"void_bow_singularity",9))return; Location c=aimedLocation(p,28); World w=p.getWorld(); w.playSound(c,Sound.ENTITY_WARDEN_SONIC_CHARGE,1.5f,0.7f); w.spawnParticle(Particle.REVERSE_PORTAL,c,90,1.2,1.2,1.2,0.12); for(LivingEntity t:targets(c,4.5,p)){damage(t,p,14);Vector v=c.toVector().subtract(t.getLocation().toVector()).normalize().multiply(1.1);v.setY(0.35);t.setVelocity(v);} w.spawnParticle(Particle.EXPLOSION,c,1); }
    private void voidBowRain(Player p) { if(!ready(p,"void_bow_rain",24))return; Location c=aimedLocation(p,26); World w=p.getWorld(); w.playSound(c,Sound.BLOCK_BEACON_ACTIVATE,1.2f,0.55f); new BukkitRunnable(){int n=0;public void run(){if(n++>=7){cancel();return;} Location q=c.clone().add((Math.random()-0.5)*7,12,(Math.random()-0.5)*7); w.spawnParticle(Particle.PORTAL,q,18,0.2,0.2,0.2,0.04); new BukkitRunnable(){public void run(){w.spawnParticle(Particle.SOUL_FIRE_FLAME,q,22,0.6,0.3,0.6,0.03);for(LivingEntity t:targets(q,2.2,p)){damage(t,p,7);t.setVelocity(new Vector(0,-0.5,0));}w.spawnParticle(Particle.EXPLOSION,q,1);}}.runTaskLater(UltimateArtifacts.this,12L);}}.runTaskTimer(this,0,5); }
    private void voidBowPhase(Player p) { if(!ready(p,"void_bow_phase",14))return; Location from=p.getLocation(); Vector d=from.getDirection().normalize(); Location to=from.clone().add(d.clone().multiply(16)); while((to.getBlock().isSolid()||to.clone().add(0,1,0).getBlock().isSolid())&&to.distanceSquared(from)>4)to.subtract(d.clone().multiply(0.5)); p.getWorld().spawnParticle(Particle.PORTAL,from,80,0.7,1,0.7,0.2); p.teleport(to); p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,to,100,1,1,1,0.2); p.playSound(to,Sound.ENTITY_ENDERMAN_TELEPORT,1.4f,0.6f); }
    private void voidBowSoulPiercer(Player p) { if(!ready(p,"void_bow_piercer",18))return; Location eye=p.getEyeLocation(); Vector d=eye.getDirection(); World w=p.getWorld(); w.playSound(eye,Sound.ENTITY_WARDEN_SONIC_BOOM,1.2f,0.9f); for(int i=1;i<=28;i++){Location q=eye.clone().add(d.clone().multiply(i));w.spawnParticle(Particle.SOUL,q,4,0.08,0.08,0.08,0.01);for(LivingEntity t:targets(q,0.8,p)){damage(t,p,18);t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,50,1));}} }
    private void voidBowEclipse(Player p) { if(!ready(p,"void_bow_eclipse",65))return; Location c=aimedLocation(p,30); World w=p.getWorld(); w.playSound(c,Sound.ENTITY_WITHER_SPAWN,1.5f,0.55f); new BukkitRunnable(){int t=0;public void run(){if(t++>=50){for(LivingEntity e:targets(c,10,p)){damage(e,p,24);e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,50,0));}w.spawnParticle(Particle.EXPLOSION_EMITTER,c,2);w.playSound(c,Sound.ENTITY_GENERIC_EXPLODE,2f,0.5f);cancel();return;}double r=0.2+t*0.16;for(int i=0;i<36;i++){double a=i*Math.PI/18;Location q=c.clone().add(Math.cos(a)*r,Math.sin(t*0.12),Math.sin(a)*r);w.spawnParticle(Particle.REVERSE_PORTAL,q,2,0,0,0,0.01);}if(t%5==0)w.spawnParticle(Particle.SOUL_FIRE_FLAME,c,20,2,1,2,0.03);}}.runTaskTimer(this,0,1); }
    private void thunderBowStormArrow(Player p) { if(!ready(p,"thunder_bow_arrow",8))return; Location c=aimedLocation(p,28); World w=p.getWorld(); w.strikeLightningEffect(c); w.spawnParticle(Particle.ELECTRIC_SPARK,c,100,1.5,1,1.5,0.18); for(LivingEntity t:targets(c,4,p)){damage(t,p,12);t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,50,2));} w.playSound(c,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1.4f,1f); }
    private void thunderBowVolley(Player p) { if(!ready(p,"thunder_bow_volley",20))return; Location eye=p.getEyeLocation(); Vector dir=eye.getDirection(); World w=p.getWorld(); w.playSound(eye,Sound.ITEM_CROSSBOW_SHOOT,1.3f,1.3f); for(int i=-2;i<=2;i++){Vector spread=dir.clone().rotateAroundY(Math.toRadians(i*7)); Location end=eye.clone().add(spread.multiply(22)); w.spawnParticle(Particle.ELECTRIC_SPARK,end,24,0.5,0.5,0.5,0.1); for(LivingEntity t:targets(end,2.2,p)){damage(t,p,7);w.strikeLightningEffect(t.getLocation());}} }
    private void thunderBowMark(Player p) { if(!ready(p,"thunder_bow_mark",28))return; Location c=aimedLocation(p,30); World w=p.getWorld(); w.playSound(c,Sound.BLOCK_BEACON_POWER_SELECT,1.3f,0.7f); w.spawnParticle(Particle.ELECTRIC_SPARK,c,70,2,2,2,0.12); new BukkitRunnable(){int tick=0;public void run(){if(tick++>=80){cancel();return;}if(tick%10==0){List<LivingEntity> ts=targets(c,7,p);for(LivingEntity t:ts){w.strikeLightningEffect(t.getLocation());damage(t,p,3);}}}}.runTaskTimer(this,0,2); }

    @EventHandler public void onArtifactArrow(ProjectileLaunchEvent e) { if(!(e.getEntity().getShooter() instanceof Player p))return; String weapon=id(p.getInventory().getItemInMainHand()); if(!"void_bow".equals(weapon)&&!"thunder_bow".equals(weapon))return; Projectile projectile=e.getEntity(); projectile.setCustomName(weapon); projectile.setCustomNameVisible(false); new BukkitRunnable(){public void run(){if(!projectile.isValid()||projectile.isOnGround()||projectile.isDead()){cancel();return;}Location q=projectile.getLocation();if("void_bow".equals(weapon)){q.getWorld().spawnParticle(Particle.REVERSE_PORTAL,q,5,0.08,0.08,0.08,0.01);q.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,q,2,0.03,0.03,0.03,0.005);}else{q.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,q,7,0.08,0.08,0.08,0.02);}}}.runTaskTimer(UltimateArtifacts.this,0L,1L); }
    @EventHandler public void onArtifactArrowHit(ProjectileHitEvent e) { if(!(e.getEntity().getShooter() instanceof Player p))return; String weapon=e.getEntity().getCustomName(); if(weapon==null)return; Location at=e.getEntity().getLocation(); if("void_bow".equals(weapon)){at.getWorld().spawnParticle(Particle.REVERSE_PORTAL,at,45,0.8,0.8,0.8,0.1);for(LivingEntity t:targets(at,2.5,p)){damage(t,p,5);Vector v=at.toVector().subtract(t.getLocation().toVector()).normalize().multiply(0.6);t.setVelocity(v.setY(0.2));}}else if("thunder_bow".equals(weapon)){at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at,55,0.8,0.8,0.8,0.12);for(LivingEntity t:targets(at,2.5,p)){at.getWorld().strikeLightningEffect(t.getLocation());damage(t,p,4);}} }

    private void shield(Player p){if(!ready(p,"shield",35))return;p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,3));p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,4));p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,1.5f,0.7f);new BukkitRunnable(){int t=0;public void run(){if(t++>=100){cancel();return;}Location l=p.getLocation().add(0,1,0);for(int i=0;i<24;i++){double a=i*Math.PI/12;p.getWorld().spawnParticle(Particle.END_ROD,l.clone().add(Math.cos(a)*1.5,Math.sin(t*0.08),Math.sin(a)*1.5),1);}}}.runTaskTimer(this,0,2);}
    private void riftDash(Player p){if(!ready(p,"rift",12))return;Location from=p.getLocation();Vector d=from.getDirection().normalize();Location to=from.clone().add(d.multiply(15));while(to.getBlock().isSolid()||to.clone().add(0,1,0).getBlock().isSolid()){to.subtract(from.getDirection().normalize());if(to.distanceSquared(from)<4)return;}p.getWorld().spawnParticle(Particle.PORTAL,from.add(0,1,0),100,1,1,1,0.4);p.teleport(to);p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,to.add(0,1,0),100,1,1,1,0.4);p.getWorld().playSound(to,Sound.ENTITY_ENDERMAN_TELEPORT,1.5f,0.8f);}
    private void timeStop(Player p){if(!ready(p,"time",45))return;for(LivingEntity t:targets(p.getLocation(),8,p)){t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,6));t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,100,2));}p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,2f,0.5f);p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,1));}
    private void berserk(Player p){if(!ready(p,"rage",30))return;p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,160,2));p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,160,1));p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,160,0));p.setHealth(Math.max(1,p.getHealth()-Math.min(5,p.getHealth()-1)));p.getWorld().playSound(p.getLocation(),Sound.ENTITY_RAVAGER_ROAR,1.5f,0.8f);}
    private void frostPrison(Player p){if(!ready(p,"frost",25))return;for(LivingEntity t:targets(p.getLocation(),6,p)){t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,8));t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,100,128));t.setFreezeTicks(Math.min(t.getMaxFreezeTicks(),100));t.getWorld().spawnParticle(Particle.SNOWFLAKE,t.getLocation().add(0,1,0),50,0.8,1,0.8,0.04);}p.getWorld().playSound(p.getLocation(),Sound.BLOCK_GLASS_BREAK,1.4f,0.6f);}
    private void summon(Player p){if(!ready(p,"summon",60))return;IronGolem g=p.getWorld().spawn(p.getLocation().add(p.getLocation().getDirection().multiply(2)),IronGolem.class);g.setCustomName(ChatColor.DARK_GRAY+"Страж пустоты");g.setCustomNameVisible(true);g.setPlayerCreated(true);g.setTarget(null);g.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,600,0));new BukkitRunnable(){int t=0;public void run(){if(!g.isValid()||t++>=600){if(g.isValid())g.remove();cancel();return;}if(t%20==0){LivingEntity nearest=null;double best=64;for(LivingEntity x:targets(g.getLocation(),8,p)){double d=x.getLocation().distanceSquared(g.getLocation());if(d<best){best=d;nearest=x;}}if(nearest!=null)g.setTarget(nearest);}}}.runTaskTimer(this,0,1);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_IRON_GOLEM_REPAIR,1.5f,0.6f);}

    @EventHandler public void onDamage(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player p))return;String id=id(p.getInventory().getItemInMainHand());if("blood_dagger".equals(id)){double heal=Math.min(3,e.getFinalDamage()*0.25);p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(),p.getHealth()+heal));p.getWorld().spawnParticle(Particle.HEART,e.getEntity().getLocation().add(0,1,0),5,0.3,0.3,0.3);}
        if("thunder_blade".equals(id)){if(ready(p,"chain",2)){LivingEntity hit=e.getEntity() instanceof LivingEntity l?l:null;if(hit!=null){p.getWorld().strikeLightningEffect(hit.getLocation());damage(hit,p,4);for(LivingEntity other:targets(hit.getLocation(),4,p))if(other!=hit){p.getWorld().strikeLightningEffect(other.getLocation());damage(other,p,3);}}}}
        if("void_scythe".equals(id)){int s=souls.getOrDefault(p.getUniqueId(),0);if(s>0){souls.put(p.getUniqueId(),s-1);e.setDamage(e.getDamage()+s*0.8);p.getWorld().spawnParticle(Particle.SOUL,e.getEntity().getLocation().add(0,1,0),25,0.5,0.5,0.5,0.05);}}
        if (e.getEntity() instanceof LivingEntity victim && ("void_mace".equals(id) || "thunder_mace".equals(id))) {
            double fall = Math.max(0, p.getFallDistance());
            if (fall > 1.0) {
                double bonus = Math.min(28.0, fall * ("void_mace".equals(id) ? 1.8 : 1.25));
                e.setDamage(e.getDamage() + bonus);
                Location hit = victim.getLocation().add(0, 0.7, 0); World w = victim.getWorld();
                if ("void_mace".equals(id)) {
                    w.playSound(hit, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.1f, 0.55f);
                    w.spawnParticle(Particle.REVERSE_PORTAL, hit, 100, 1.8, 0.8, 1.8, 0.16);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, hit, 65, 1.2, 0.5, 1.2, 0.08);
                    w.spawnParticle(Particle.DUST, hit, 50, 1.3, 0.2, 1.3, 0.0, new Particle.DustOptions(Color.fromRGB(80, 0, 150), 1.8f));
                    for (LivingEntity nearby : targets(hit, Math.min(6, 2.5 + fall * 0.15), p)) if (nearby != victim) { damage(nearby, p, Math.min(12, fall * 0.55)); Vector pull = hit.toVector().subtract(nearby.getLocation().toVector()).normalize().multiply(0.55); pull.setY(0.25); nearby.setVelocity(pull); }
                    victim.setVelocity(victim.getVelocity().add(new Vector(0, Math.min(0.8, fall * 0.025), 0)));
                } else {
                    w.playSound(hit, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.75f);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, hit, 120, 2.0, 0.8, 2.0, 0.2);
                    w.spawnParticle(Particle.FLASH, hit, 2);
                    for (LivingEntity nearby : targets(hit, Math.min(7, 3 + fall * 0.18), p)) { w.strikeLightningEffect(nearby.getLocation()); damage(nearby, p, Math.min(10, fall * 0.4)); }
                }
                p.setFallDistance(0);
            }
        }
    }
    @EventHandler public void onKill(EntityDeathEvent e){Player p=e.getEntity().getKiller();if(p!=null && has(p,"void_scythe")){int value=Math.min(10,souls.getOrDefault(p.getUniqueId(),0)+1);souls.put(p.getUniqueId(),value);p.sendActionBar(ChatColor.DARK_PURPLE+"Души пустоты: "+ChatColor.LIGHT_PURPLE+value+"/10");p.getWorld().spawnParticle(Particle.SOUL,p.getLocation().add(0,1,0),18,0.4,0.6,0.4,0.05);}}
    @EventHandler public void onFatal(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p)||!has(p,"phoenix_feather")||p.getHealth()-e.getFinalDamage()>0)return;long now=System.currentTimeMillis();if(phoenixUsed.getOrDefault(p.getUniqueId(),0L)>now)return;phoenixUsed.put(p.getUniqueId(),now+3600000);e.setCancelled(true);p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(),10));p.setFireTicks(0);p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),100,1,1,1,0.5);p.getWorld().playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,2f,0.7f);}

    private org.bukkit.configuration.file.FileConfiguration config(){return getConfig();}
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a) {
        if (a.length == 0) { s.sendMessage(ChatColor.LIGHT_PURPLE + "/artifacts menu | give <player> <item> [amount] | list | giveall <player> | reload"); return true; }
        if (a[0].equalsIgnoreCase("menu")) {
            if (!(s instanceof Player p)) { s.sendMessage("Команда доступна только игроку."); return true; }
            String held = id(p.getInventory().getItemInMainHand());
            if (!isConfigurableWeapon(held)) { p.sendMessage(ChatColor.RED + "Возьми в основную руку Серп, булаву или легендарный лук."); return true; }
            openWeaponMenu(p, held); return true;
        }
        if (!s.hasPermission("artifacts.admin")) { s.sendMessage(ChatColor.RED + "Нет прав."); return true; }
        switch (a[0].toLowerCase()) {
            case "list" -> { for (int i=0;i<IDS.length;i++) s.sendMessage(ChatColor.LIGHT_PURPLE+IDS[i]+ChatColor.GRAY+" — "+NAMES[i]); return true; }
            case "reload" -> { reloadConfig(); s.sendMessage(ChatColor.GREEN+"Конфигурация перезагружена."); return true; }
            case "giveall" -> { if(a.length<2)return false; Player target=Bukkit.getPlayerExact(a[1]); if(target==null){s.sendMessage(ChatColor.RED+"Игрок не найден.");return true;} for(int i=0;i<IDS.length;i++)target.getInventory().addItem(makeItem(i)); s.sendMessage(ChatColor.GREEN+"Выданы все "+IDS.length+" артефактов."); return true; }
            case "give" -> { if(a.length<3)return false; Player target=Bukkit.getPlayerExact(a[1]); if(target==null){s.sendMessage(ChatColor.RED+"Игрок не найден.");return true;} int idx=Arrays.asList(IDS).indexOf(a[2].toLowerCase()); if(idx<0){s.sendMessage(ChatColor.RED+"Неизвестный предмет. /artifacts list");return true;} int amount=1; try{if(a.length>3)amount=Math.max(1,Math.min(64,Integer.parseInt(a[3])));}catch(NumberFormatException ignored){} ItemStack item=makeItem(idx); item.setAmount(amount); target.getInventory().addItem(item); s.sendMessage(ChatColor.GREEN+"Выдано: "+NAMES[idx]); return true; }
            default -> { s.sendMessage(ChatColor.RED+"Неизвестная подкоманда."); return true; }
        }
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args) {
        if(args.length==1)return List.of("menu","give","giveall","list","reload").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();
        if(args.length==2&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("giveall")))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        if(args.length==3&&args[0].equalsIgnoreCase("give"))return Arrays.stream(IDS).filter(x->x.startsWith(args[2].toLowerCase())).toList();
        return List.of();
    }
}
