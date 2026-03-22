package hu.bandibandus10;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;

public final class SpiritSMP extends JavaPlugin implements Listener {

    private final Set<UUID> deadPlayers = new HashSet<>();
    private World spiritWorld;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        if (getConfig().getKeys(false).isEmpty()) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        }
        loadDeadPlayers();
        getServer().getPluginManager().registerEvents(this, this);
        createSpiritWorld();
    }

    @Override
    public void onDisable() {
        saveDeadPlayers();
    }

    private void loadDeadPlayers() {
        deadPlayers.clear();
        getConfig().getStringList("dead").forEach(str -> {
            try { deadPlayers.add(UUID.fromString(str)); } catch (Exception ignored) {}
        });
    }

    private void saveDeadPlayers() {
        getConfig().set("dead", deadPlayers.stream().map(UUID::toString).toList());
        saveConfig();
    }

    private void createSpiritWorld() {
        spiritWorld = Bukkit.getWorld("spirit_world");
        if (spiritWorld == null) {
            WorldCreator wc = new WorldCreator("spirit_world");
            wc.environment(World.Environment.NORMAL);
            wc.generateStructures(false);
            wc.biomeProvider(new PaleGardenProvider());
            spiritWorld = wc.createWorld();

            if (spiritWorld != null) {
                spiritWorld.setGameRule(GameRule.DO_MOB_SPAWNING, true);
                spiritWorld.setGameRule(GameRule.KEEP_INVENTORY, false);
                spiritWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            }
        } else {
            spiritWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spirit")) {
            if (args.length == 0) return false;

            if (args[0].equalsIgnoreCase("getspirit") && args.length == 2) {
                if (!sender.hasPermission("spiritsmp.admin")) {
                    sender.sendMessage(ChatColor.RED + "Nincs jogod!");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Játékos nem online!");
                    return true;
                }
                if (sender instanceof Player) {
                    giveSoulItem((Player) sender, target);
                } else {
                    sender.sendMessage(ChatColor.RED + "Csak játékos használhatja!");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("revive") && args.length == 2) {
                if (!sender.hasPermission("spiritsmp.admin")) {
                    sender.sendMessage(ChatColor.RED + "Nincs jogod!");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (target == null || !target.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED + "Érvénytelen játékos!");
                    return true;
                }
                UUID uuid = target.getUniqueId();
                if (!deadPlayers.contains(uuid)) {
                    sender.sendMessage(ChatColor.RED + target.getName() + " nem halott!");
                    return true;
                }
                deadPlayers.remove(uuid);
                sender.sendMessage(ChatColor.GREEN + "Feltámasztottad " + target.getName() + "-t! Amikor belép, a spawn-on fog megjelenni.");

                if (target.isOnline()) {
                    Player onlineTarget = target.getPlayer();
                    if (onlineTarget != null) {
                        onlineTarget.getInventory().clear();
                        onlineTarget.teleport(onlineTarget.getWorld().getSpawnLocation());
                        onlineTarget.setGameMode(GameMode.SURVIVAL);
                        onlineTarget.sendMessage(ChatColor.GREEN + "Feltámasztottak! Most újra élsz!");
                    }
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("testdeath")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Csak játékos használhatja!");
                    return true;
                }
                deadPlayers.add(player.getUniqueId());
                player.getInventory().clear();
                player.sendMessage(ChatColor.RED + "Teszt halál aktiválva.");
                return true;
            }
        }
        return false;
    }

    private void giveSoulItem(Player receiver, Player victim) {
        ItemStack soul = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta m = soul.getItemMeta();
        m.setDisplayName(ChatColor.AQUA + victim.getName() + " lelke");
        m.setLore(List.of(
                ChatColor.GRAY + victim.getName() + " vesztesége",
                ChatColor.GRAY + "Jobb klikk egy blokkra a feltámasztáshoz"
        ));
        m.setCustomModelData(1);
        soul.setItemMeta(m);
        receiver.getInventory().addItem(soul);
        receiver.sendMessage(ChatColor.GREEN + "Megkaptad " + victim.getName() + " lelkét!");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            ItemStack soul = new ItemStack(Material.HEART_OF_THE_SEA);
            ItemMeta m = soul.getItemMeta();
            m.setDisplayName(ChatColor.AQUA + victim.getName() + " lelke");
            m.setLore(List.of(
                    ChatColor.GRAY + victim.getName() + " vesztesége",
                    ChatColor.GRAY + killer.getName() + " dicsősége"
            ));
            m.setCustomModelData(1);
            soul.setItemMeta(m);
            victim.getWorld().dropItemNaturally(victim.getLocation(), soul);
        }

        deadPlayers.add(victim.getUniqueId());
        victim.getInventory().clear();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        if (deadPlayers.contains(player.getUniqueId())) {
            if (spiritWorld != null) {
                int highestY = spiritWorld.getHighestBlockYAt(0, 0);
                Location tpLoc = new Location(spiritWorld, 0.5, highestY + 1, 0.5);
                e.setRespawnLocation(tpLoc);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.teleport(tpLoc);
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                }.runTaskLater(this, 1L);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isDead(p)) {
            ItemStack item = e.getItem();
            if (item != null) {
                if (item.getType() == Material.ENDER_PEARL && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                    e.setCancelled(true);
                    return;
                }
                if (item.getType() == Material.FLINT_AND_STEEL || item.getType() == Material.FIRE_CHARGE) {
                    e.setCancelled(true);
                    return;
                }
            }
            if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.ENDER_CHEST) {
                e.setCancelled(true);
                return;
            }
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null || e.getItem().getType() != Material.HEART_OF_THE_SEA) return;

        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1) return;
        if (!meta.hasLore()) return;

        List<String> lore = meta.getLore();
        if (lore.isEmpty()) return;

        String firstLine = ChatColor.stripColor(lore.get(0));
        if (!firstLine.endsWith(" vesztesége")) return;

        String originalVictimName = firstLine.substring(0, firstLine.length() - 11).trim();

        Player dead = Bukkit.getPlayerExact(originalVictimName);
        if (dead == null) {
            e.getPlayer().sendMessage(ChatColor.RED + "A játékos nincs online!");
            return;
        }
        if (!deadPlayers.contains(dead.getUniqueId())) {
            e.getPlayer().sendMessage(ChatColor.RED + "A játékos már nem halott!");
            return;
        }

        Block b = e.getClickedBlock();
        Location spawnLoc = b.getRelative(e.getBlockFace()).getLocation().add(0.5, 0, 0.5);

        dead.getInventory().clear();
        dead.teleport(spawnLoc);
        dead.setGameMode(GameMode.SURVIVAL);
        deadPlayers.remove(dead.getUniqueId());

        item.setAmount(item.getAmount() - 1);
        e.getPlayer().sendMessage(ChatColor.GREEN + "Feltámasztottad " + dead.getName() + "-t!");
        dead.sendMessage(ChatColor.GREEN + "Feltámasztottak! Most újra élsz!");
        e.setCancelled(true);
    }

    @EventHandler
    public void onSoulBurn(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Item itemEntity)) return;
        ItemStack stack = itemEntity.getItemStack();
        if (stack.getType() == Material.HEART_OF_THE_SEA
                && stack.hasItemMeta()
                && stack.getItemMeta().hasCustomModelData()
                && stack.getItemMeta().getCustomModelData() == 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSoulDespawn(ItemDespawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (stack.getType() == Material.HEART_OF_THE_SEA
                && stack.hasItemMeta()
                && stack.getItemMeta().hasCustomModelData()
                && stack.getItemMeta().getCustomModelData() == 1) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST && isDead((Player) event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.getBlock().getWorld() == spiritWorld && (event.getBlock().getType() == Material.PALE_MOSS_BLOCK || event.getBlock().getType() == Material.PALE_MOSS_CARPET)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getBlock().getWorld() == spiritWorld && (event.getBlock().getType() == Material.PALE_MOSS_BLOCK || event.getBlock().getType() == Material.PALE_MOSS_CARPET || event.getBlock().getType() == Material.OBSIDIAN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlaceFire(BlockPlaceEvent event) {
        if (event.getBlock().getWorld() != spiritWorld || event.getBlock().getType() != Material.FIRE) return;
        Block below = event.getBlock().getRelative(0, -1, 0);
        if (below.getType() == Material.OBSIDIAN || below.getType() == Material.PALE_MOSS_BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFlintSteel(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasItem() || event.getItem().getType() != Material.FLINT_AND_STEEL) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getWorld() != spiritWorld || clicked.getType() == Material.OBSIDIAN || clicked.getType() == Material.PALE_MOSS_BLOCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireChargeHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SmallFireball)) return;
        if (event.getEntity().getLocation().getWorld() != spiritWorld) return;
        Block hit = event.getHitBlock();
        if (hit != null && (hit.getType() == Material.OBSIDIAN || hit.getType() == Material.PALE_MOSS_BLOCK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onPortal(PlayerPortalEvent e) { if (isDead(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) {
        if (!isDead(e.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
                c == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
                c == PlayerTeleportEvent.TeleportCause.END_PORTAL ||
                c == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            e.setCancelled(true);
        }
    }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && isDead(p)) e.setCancelled(true); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { if (deadPlayers.contains(e.getPlayer().getUniqueId())) forceToSpiritWorld(e.getPlayer()); }
    private boolean isDead(Player p) { return deadPlayers.contains(p.getUniqueId()) && p.getWorld().equals(spiritWorld); }

    private void forceToSpiritWorld(Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                p.getInventory().clear();
                p.setGameMode(GameMode.SURVIVAL);
                int highestY = spiritWorld.getHighestBlockYAt(0, 0);
                Location tpLoc = new Location(spiritWorld, 0.5, highestY + 1, 0.5);
                p.teleport(tpLoc);
            }
        }.runTaskLater(this, 10L);
    }

    public static class PaleGardenProvider extends BiomeProvider {
        @Override public Biome getBiome(WorldInfo wi, int x, int y, int z) { return Biome.PALE_GARDEN; }
        @Override public @NotNull List<Biome> getBiomes(@NotNull WorldInfo wi) { return List.of(Biome.PALE_GARDEN); }
    }
}