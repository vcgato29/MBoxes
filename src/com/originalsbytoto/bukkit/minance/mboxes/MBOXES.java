package com.originalsbytoto.bukkit.minance.mboxes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import java.sql.*;

public class MBOXES extends JavaPlugin implements Listener {

	/*Définition des variables pour Vault*/
	private static final Logger log = Logger.getLogger("Minecraft");
	public static Economy econ = null;
	public static Permission perms = null;
	public static Chat chat = null;
	
	/*Définition des variables du plugin*/
	public static Map<String, Location> occupied = new HashMap<>(); //Liste des boites occupées pour ne pas que deux joueurs ne l'utilisent en même temps
	public static String modeste_str = "MODESTE".toLowerCase(); //Noms internes de types (pour qu'il ny ait pas de diff�rences)
	public static String normal_str = "NORMAL".toLowerCase();
	public static String rare_str = "RARE".toLowerCase();
	public static String epic_str = "EPIC".toLowerCase();
	public static String legendaire_str = "LEGENDAIRE".toLowerCase();
	public static String NOPICK_NAME = "nopickitem"; //Nom des items qui ne se font pas récupérer (nether star ...)
	public static List<Item> ITEMS = new ArrayList<>(); //Liste des nether star pour les supprimer si le delete Y=0 ne marche pas

	public Map<String, Map<String, Pair<Float, List<String>>>> prices = new HashMap<>();
	//Liste des cadeaux : La première Map<String, AutreMap> contient les maps pour chaque type de boite
	//La 2eme Map contient les ids et une Pair (une class que j'ai fait) qui contient la chance de l'obtenir et une liste de commandes si c'est gagné

	
	//Fonction pour enregistrer les prix utilisée par les autres plugin
	public void registerPrice(List<String> commands, float modeste, float normal, float rare, float epic,
			float legendaire, String identifier) {

		getLogger().info("Register price : " + identifier);

		//Si on peux la gagner avec une boite modeste ...
		if (modeste > 0) {
			getLogger().info("    MODESTE (" + modeste + ")");
			prices.
			get(modeste_str).
			put(identifier, 
					new Pair<Float, List<String>>(modeste, commands));
			// On l'ajoute

		}

		//Pareil que modeste
		if (normal > 0) {
			getLogger().info("    NORMAL (" + normal + ")");
			prices.get(normal_str).put(identifier, new Pair<Float, List<String>>(normal, commands));

		}

		//Pareil que modeste
		if (rare > 0) {
			getLogger().info("    RARE (" + rare + ")");
			prices.get(rare_str).put(identifier, new Pair<Float, List<String>>(rare, commands));

		}

		//Pareil que modeste
		if (epic > 0) {
			getLogger().info("    EPIC (" + epic + ")");
			prices.get(epic_str).put(identifier, new Pair<Float, List<String>>(epic, commands));

		}

		//Pareil que modeste
		if (legendaire > 0) {
			getLogger().info("    LEGENDAIRE (" + legendaire + ")");
			prices.get(legendaire_str).put(identifier, new Pair<Float, List<String>>(legendaire, commands));

		}

	}

	//Nom pour reconnaitre l'inventaire des boites lors des item clicks
	public static String INV_NAME = "GiftBoxes - Boites à cadeaux";

	//Récupération de l'economy de Vault
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	//Pas utilisé mais c'est encore Vault
	private boolean setupChat() {
		RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
		chat = rsp.getProvider();
		return chat != null;
	}

	//Pareil que setupChat
	private boolean setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		perms = rsp.getProvider();
		return perms != null;
	}

	@Override
	//Fonction de Bukkit, elle s'execute quand le plugin est désactivé (stop ou reload)
	public void onDisable() {
		log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
	}

	@Override
	//Pareil mais au start, on d�finis les variables
	public void onEnable() {
		//Créé le fichier config.yml
		saveDefaultConfig();
		
		//On créé les deuxièmes Map pour chaque type de boite
		prices.put(modeste_str, new HashMap<String, Pair<Float, List<String>>>());
		prices.put(normal_str, new HashMap<String, Pair<Float, List<String>>>());
		prices.put(rare_str, new HashMap<String, Pair<Float, List<String>>>());
		prices.put(epic_str, new HashMap<String, Pair<Float, List<String>>>());
		prices.put(legendaire_str, new HashMap<String, Pair<Float, List<String>>>());
		
		log.info("[BUKKIT-SIDE] MinAnce MBOXES en cours de chargement ...");
		
		//Dis � Bukkit qu'on veut qu'il nous pr�vienne des events (sert pour inventroy clicks)
		getServer().getPluginManager().registerEvents(this, this);
		
		//Param�tres de Vault
		if (!setupEconomy()) {
			log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		//Vérifie les nether start en créant une boucle qui commence dans 0 ticks qui va d'éxecuter toutes les
		//2 secondes (2*20 ticks) Attention : ticks de jeu, pas de redstone
		getServer().getScheduler().runTaskTimer(this, new Runnable() {
			
			@Override
			public void run() {
				for(int i = 0; i < ITEMS.size(); i++) {
					
					Location loc = ITEMS.get(i).getLocation();
					//Récupère la position de l'item jeté et met le à Y=0
					loc.setY(0);
					//Téléporte l'item à sa nouvelle pos (il n'y a pas de setLocation();, c'est teleport())
					ITEMS.get(i).teleport(loc);
					
				}
			}
		}, 0 /*Commence dans*/, 2*20/*Tous les ... ticks de jeu*/);
		
		/*setupPermissions();
		setupChat();*/

	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {

		//On se sert des events
		//Si on clic droit sur un bloc
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {

			//Et que le bloc est un ENDER_PORTAL_FRAME
			//la 2ème condition est pour éviter le bug de l'exécution double
			//car on clic droit 2 fois : une fois client et une fois serveur, donc je ne vérifie qu'une boite
			//n'est pas ouverte par ce joueur
			if (event.getClickedBlock().getType() == Material.ENDER_PORTAL_FRAME
					&& occupied.containsKey(event.getPlayer().getName()) == false) {

				//Je vérifie si la boite est occupée
				if (occupied.containsValue(event.getClickedBlock().getLocation())) {

					event.getPlayer().sendMessage(
							"§cCette boite est déjà en cours d'utilisation. Trouves-en une autre ou attend un peu");

				} else {

					//Sinon, je l'ajoute aux boites occupées
					occupied.put(event.getPlayer().getName(), event.getClickedBlock().getLocation());
					//Et j'ouvre le menu (voir menu(Player player);)
					menu(event.getPlayer());

				} //End boite occupée

			} //End clic droit sur ...

		}//End vérif clic droit et pas clic gauche

	}

	
	//Ouvre le menu à player
	public void menu(Player player) {

		String uuid = player.getUniqueId().toString();
		
		//Créé la config 0,0,0,0,0 si le joueur ouvre les boites pour la 1ère fois
		createUserIfNotExist(uuid);

		//Je créé l'inventaire à ouvrir
		Inventory inv = Bukkit.createInventory(null/*Tjs mettre null*/, 3 * 9/*Nombre de cases. Obligé un multiple de 9*/, INV_NAME/*Nom en haut*/);

		ItemStack[] items = inv.getContents();

		//Je récupère le nombre de boites de chaque type
		int legendaire = getConfig().getInt("players." + uuid + ".legendaire");
		int epic = getConfig().getInt("players." + uuid + ".epic");
		int rare = getConfig().getInt("players." + uuid + ".rare");
		int normal = getConfig().getInt("players." + uuid + ".normal");
		int modeste = getConfig().getInt("players." + uuid + ".modeste");

		//Pour chaque type, si il a des boites, je met un coffre
		if (modeste != 0) {
			//130 = coffre de l'end
			items[11] = new ItemStack(130);
			ItemMeta imm = items[11].getItemMeta(); //Récupération des metas
			//Set name
			imm.setDisplayName("§7Boite modeste");
			//Création de liste pour le lore
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§fQuantité : " + modeste);
			imm.setLore(imml); //Set lore
			items[11].setAmount(modeste); //je set le nombre de coffre en fonction du nombre de boites
			items[11].setItemMeta(imm); //set meta

		} else {

			//Sinon, je fais pareil mais avec une vitre rouge
			items[11] = new ItemStack(160);
			ItemMeta imm = items[11].getItemMeta();
			imm.setDisplayName("§4Aucune boite modeste");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§cTu n'as aucune boite modeste.");
			imml.add("§cJoue à des jeux pour en gagner.");
			imm.setLore(imml);
			items[11].setDurability((short) 14);
			items[11].setItemMeta(imm);

		}

		if (normal != 0) {

			items[12] = new ItemStack(130);
			ItemMeta imm = items[12].getItemMeta();
			imm.setDisplayName("§fBoite normale");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§fQuantité : " + normal);
			imm.setLore(imml);
			items[12].setAmount(normal);
			items[12].setItemMeta(imm);

		} else {

			items[12] = new ItemStack(160);
			ItemMeta imm = items[12].getItemMeta();
			imm.setDisplayName("§4Aucune boite normale");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§cTu n'as aucune boite normale.");
			imml.add("§cJoue à des jeux pour en gagner.");
			imm.setLore(imml);
			items[12].setDurability((short) 14);
			items[12].setItemMeta(imm);

		}

		if (rare != 0) {

			items[13] = new ItemStack(130);
			ItemMeta imm = items[13].getItemMeta();
			imm.setDisplayName("§1Boite rare");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§fQuantité : " + rare);
			imm.setLore(imml);
			items[13].setAmount(rare);
			items[13].setItemMeta(imm);

		} else {

			items[13] = new ItemStack(160);
			ItemMeta imm = items[13].getItemMeta();
			imm.setDisplayName("§4Aucune boite rare");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§cTu n'as aucune boite rare.");
			imml.add("§cJoue à des jeux pour en gagner.");
			imm.setLore(imml);
			items[13].setDurability((short) 14);
			items[13].setItemMeta(imm);

		}

		if (epic != 0) {

			items[14] = new ItemStack(130);
			ItemMeta imm = items[14].getItemMeta();
			imm.setDisplayName("§5Boite épique");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§fQuantité : " + epic);
			imm.setLore(imml);
			items[14].setAmount(epic);
			items[14].setItemMeta(imm);

		} else {

			items[14] = new ItemStack(160);
			ItemMeta imm = items[14].getItemMeta();
			imm.setDisplayName("§4Aucune boite épique");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§cTu n'as aucune boite épique.");
			imml.add("§cJoue à des jeux pour en gagner.");
			imm.setLore(imml);
			items[14].setDurability((short) 14);
			items[14].setItemMeta(imm);

		}

		if (legendaire != 0) {

			items[15] = new ItemStack(130);
			ItemMeta imm = items[15].getItemMeta();
			imm.setDisplayName("§6Boite légendaire");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§fQuantité : " + legendaire);
			imm.setLore(imml);
			items[15].setAmount(legendaire);
			items[15].setItemMeta(imm);

		} else {

			items[15] = new ItemStack(160);
			ItemMeta imm = items[15].getItemMeta();
			imm.setDisplayName("§4Aucune boite légendaire");
			List<String> imml = new ArrayList<String>();
			imml.add(" ");
			imml.add("§cTu n'as aucune boite légendaire.");
			imml.add("§cJoue à des jeux pour en gagner.");
			imm.setLore(imml);
			items[15].setDurability((short) 14);
			items[15].setItemMeta(imm);

		}
		
		//Set contents avec nos items
		inv.setContents(items);
		
		//Ouvre l'inventaire
		player.openInventory(inv);

	}

	//onCommand dois tjs avoir CES arguments et retourner un boolean. Je te conseille de tjs mettre true
	//sinon tu vois : An erreur occured while attempting to perform this command
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		//Si la commande est mboxes (il faut la définir dans plugin.yml) cette commande me sert à moi et
		//chaque commande est différente. Pour là, je te conseille de suivre un tuto
		if (cmd.getName().equalsIgnoreCase("mboxes")) {

			//Moi, je vérifie si sender est op
			if (sender.isOp()) {
				
				if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

					//Recharge la config
					reloadConfig();
					sender.sendMessage("§2Configuration rechargée");

				} else if (args.length == 4) {

					if (args[0].equalsIgnoreCase("give")) {

						String pname = args[1];
						String type = args[2];
						String qstr = args[3];

						int qte = Integer.parseInt(qstr);

						Player player = getServer().getPlayer(pname);

						if (player != null) {

							String uuid = player.getUniqueId().toString();

							switch (type) {

							case "epic":
							case "epique":
							case "épique":
							case "épic":
								give(uuid, "epic", qte);
								break;

							case "legendaire":
							case "légendaire":
								give(uuid, "legendaire", qte);
								break;

							case "normal":
							case "normale":
								give(uuid, "normal", qte);
								break;

							case "rare":
								give(uuid, "rare", qte);
								break;

							case "modeste":
								give(uuid, "modeste", qte);
								break;

							}

							sender.sendMessage("§2Boites données.");

						} else {

							sender.sendMessage("§4Ce joueur n'est pas connecté");

						}

					} else {

						sender.sendMessage("§4Mauvais arguments");

					}

				} else {

					sender.sendMessage("§4Mauvais arguments");

				}

			}

		}

		return true;

	}

	//Ma fonction pour give des boites
	public void give(String uuid, String type, int qte) {

		//Encore une fois, si le joueur n'existe pas, il faut le créer
		createUserIfNotExist(uuid);

		int actual = getConfig().getInt("players." + uuid + "." + type);

		if (actual + qte < 0) {

			getConfig().set("players." + uuid + "." + type, 0);

		} else
			getConfig().set("players." + uuid + "." + type, actual + qte);

		
		//Tjs aprés un config.set();
		//Il faut sauvegarder
		saveConfig();

	}

	public void createUserIfNotExist(String uuid) {

		//Si il ne contient pas le joueur
		if (getConfig().contains("players." + uuid) == false) {

			//Crééla section qui lui correspond
			//Je te conseille d'utiliser les uuid pour les sauvegardes
			getConfig().createSection("players." + uuid);
			//Set les boites à 0
			getConfig().set("players." + uuid + ".legendaire", 0);
			getConfig().set("players." + uuid + ".epic", 0);
			getConfig().set("players." + uuid + ".rare", 0);
			getConfig().set("players." + uuid + ".normal", 0);
			getConfig().set("players." + uuid + ".modeste", 0);
			//Je save
			saveConfig();

		}

	}

	//Event si on clic sur un item dans un inventaire
	@EventHandler
	public void onPlayerInventoryClick(InventoryClickEvent event) {

		
		//Si c'est l'inv des boites (INV_NAME) et que l'on fait un clic gauche
		if (event.getInventory().getName() == INV_NAME) {

			if(event.getAction() == InventoryAction.PICKUP_ALL) {
			
				String type = "";
				ItemStack is = event.getCurrentItem();
				if (is.getType() == Material.ENDER_CHEST) {
	
					if (is.getItemMeta().getDisplayName().toLowerCase().contains("Boite".toLowerCase())) {
	
						//Get UUID
						UUID uuid = event.getWhoClicked().getUniqueId();
	
						String iname = is.getItemMeta().getDisplayName();
						//Je fais un switch en fonction du nom de l'item sur lequel on a cliqué
						//Comme j'ai des couleurs sur les noms des items, il faut les mettre dans les case
						switch (iname) {
	
						case "§6Boite légendaire":
							consume("legendaire", uuid);
							break;
						case "§5Boite épique":
							consume("epic", uuid);
							break;
						case "§1Boite rare":
							consume("rare", uuid);
							break;
						case "§fBoite normale":
							consume("normal", uuid);
							break;
						case "§7Boite modeste":
							consume("modeste", uuid);
							break;
	
						}
	
					} else {
	
						getLogger().info("noboite");
	
					}
	
				}
				
			}

			//Pour annuler le clic et ne pas qu'il garde l'item, on annule l'event
			event.setCancelled(true);

		}

	}

	
	//Ouvre une boite
	public void consume(final String type, UUID uuid) {

		//On prends la qte
		int qte = getConfig().getInt("players." + uuid.toString() + "." + type);

		getLogger().info(uuid.toString() + " : " + qte);

		//Si il peut l'ouvrir
		if (qte > 0) {

			//On diminie de 1
			int nqte = qte - 1;
			getConfig().set("players." + uuid.toString() + "." + type, nqte); //On save à la fin (j'ai pas oublié)
			// getServer().getPlayer(uuid).sendMessage("Tu as bien ouvert la
			// boite de type " + type);
			//on prend la loc de la boite grace au nom du joueur, que l'on a aussi stocké
			//getServer().getPlayer() permet de trouver un joueur en fonction de son nom ou de son uuid
			Location loc = occupied.get(getServer().getPlayer(uuid).getName());
			//Je ferme le menu
			getServer().getPlayer(uuid).closeInventory();
			//Voir onInventoryClose()
			//Je remets car la boite est encore occupée par la nether star
			occupied.put(getServer().getPlayer(uuid).getName(), loc);
			//Je créé la netherstar
			ItemStack ns = new ItemStack(399);
			ItemMeta nsim = ns.getItemMeta();
			//Voir onPickupItem()
			nsim.setDisplayName(NOPICK_NAME);
			ns.setItemMeta(nsim);

			//Je prends la loc de la boite
			Location preiloc = loc;
			//Je place la nether star au dessus et au milieu de l'ender frame
			preiloc.add(new Vector(0.5f, 1.25f, 0.5f));
			final Location iloc = preiloc;

			//Je fais spawn l'item
			final Item item = loc.getWorld().dropItemNaturally(iloc, ns);

			final Player p = getServer().getPlayer(uuid);
			//Cette boucle s'execute tous les ticks pour que la nether star reste à sa pos
			final BukkitTask task = getServer().getScheduler().runTaskTimer(this, new Runnable() {

				@Override
				public void run() {
					item.teleport(iloc);
				}
			}, 0/*0=Tout de suite*/, 1/*Tous les ... ticks de jeu*/);

			Color precolor = null;

			//Choix de la couleur de feu d'artifice
			switch (type) {

			case "legendaire":
				precolor = Color.YELLOW;
				break;
			case "epic":
				precolor = Color.BLUE;
				break;
			case "rare":
				precolor = Color.FUCHSIA;
				break;
			case "normal":
				precolor = Color.WHITE;
				break;
			case "modeste":
				precolor = Color.SILVER;
				break;

			}

			//Définition de la couleur 'final' pour la tache executée après
			//Toutes les variables utilisées dans les taches doivent être en 'final'
			final Color color = precolor;

			getServer().getScheduler().runTaskLater(this, new Runnable() {

				@Override
				public void run() {
					task.cancel();
					//On annule la boucle de tp
					//On supprime le fait que la boite est occupée
					occupied.remove(p.getName());

					Location iloc2 = item.getLocation();
					iloc2.setY(0);
					item.teleport(iloc2);
					
					//On tp la nether star dans le vide
					getLogger().info("Teleport item to " + iloc2.toString());
					
					getLogger().info(iloc2.toString());
					item.setTicksLived(32767); //Sert à le tuer (mais ne marche pas très bien)

					//J'ai pas tout compris ici c'est du copié-collé pour les feux d'artifices
					// Note rajoutée après : maintenant j'ai compris. Minecraft possède une classe FireworkBuilder pour
					// simplifier leur création.
					Firework f = (Firework) iloc.getWorld().spawn(iloc, Firework.class); //On Créé le feu d'artifice
					FireworkMeta fm = f.getFireworkMeta();
					fm.addEffect(FireworkEffect.builder().flicker(false).trail(true).withColor(Color.SILVER/*Couleur de d�but*/)
							.withFade(color/**/).build()); // On utilise le builder
					fm.setPower(0);//On ajoute un fade vers ... . On peut en mettre plusieurs
					f.setFireworkMeta(fm);

					
					//Récupération des prix dispo
					Map<String, Pair<Float, List<String>>> a_prices = prices.get(type);
					float count = 0;
					List<Pair<Float, List<String>>> a_values = new ArrayList<>(a_prices.values());
					for (int i = 0; i < a_prices.size(); i++) {

						count += a_values.get(i).getLeft();

					}
					
					getLogger().info("Max : " + count);

					//Choix du nombre aléatoire
					float nombre = 0;
					float min = 1;
					float max = count;
					while (nombre == 0)
						nombre = min + (float) (Math.random() * ((max - min) + 1));

					float n = nombre;

					getLogger().info("GEN : " + n);
					
					float actual_inc = 0;
					for (int i = 0; i < a_prices.size(); i++) {

						float inc = a_values.get(i).getLeft();
						
						//Si le nombre choisi est pour ce prix
						if(isBetween(n, actual_inc, actual_inc + inc)) {
							
							//On r�cup�re les cmds de ce prix
							List<String> cmds = a_values.get(i).getRight();
							
							getLogger().info("Between " + cmds.size());
							
							for(int z = 0; z < cmds.size(); z++) {
								
								String cmd = cmds.get(z);
								//On met le nom du joueur
								cmd = cmd.replace("%player%", p.getName());
								
								//On émett la commande getServer().getConsoleSender() pour la faire executer par la console
								getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
								
								getLogger().info("cmd : " + cmd);
								
							}
							
						}
						
						actual_inc += inc;
						getLogger().info("Set a_inc to : " + actual_inc);
						//On ajoute la nether star à la vérification
						ITEMS.add(item);

					}

				}
			}, (long) (20 * 2f));

		} else {

			getLogger().info("noqte");

		}

		//Et enfin on save
		saveConfig();

	}

	//Quand on ferme l'inventaire, la boite redevient libre, donc quand on a ouvert une boite, il faut remettre le .put() car elle est pas encore libre
	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {

		if(event.getInventory().getTitle() == INV_NAME)occupied.remove(event.getPlayer().getName());

	}

	//Pour annuler le pick
	@EventHandler
	public void onPickupItem(PlayerPickupItemEvent event) {

		//Si le nom est NOPICKUP
		if (event.getItem().getItemStack().getItemMeta().getDisplayName().toLowerCase()
				.contains(NOPICK_NAME)) {

			//Alors on annule
			event.setCancelled(true);

		}

	}

	//Pour trouver si l'aléatoire est bon
	public boolean isBetween(int origin, int min, int max) {

		return origin >= min && origin <= max;

	}
	
	//Pareil, mais pour float au lieu de int
	public boolean isBetween(float origin, float min, float max) {

		return origin >= min && origin <= max;

	}

}
