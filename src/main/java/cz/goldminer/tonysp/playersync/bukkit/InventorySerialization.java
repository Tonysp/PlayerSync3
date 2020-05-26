package cz.goldminer.tonysp.playersync.bukkit;

import cz.goldminer.tonysp.playersync.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class InventorySerialization {

    private static InventorySerialization instance;

    public InventorySerialization () {
        instance = this;
    }

    public static InventorySerialization getInstance () {
        return instance;
    }

    public PlayerData generatePlayerData (Player player) {
        return new PlayerData(player.getName(), playerInventoryToString(player), playerArmorToString(player), player.getLevel());
    }

    public boolean loadPlayerInventory (Player player, PlayerData playerData){
        Inventory inventory;
        ItemStack[] armor;
        try{
            inventory = inventoryFromBase64(playerData.getInventory());
            armor = itemStackArrayFromBase64(playerData.getArmor());
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }


        int pos = 0;
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]);
        for(ItemStack is : inventory){
            if(is == null){
                pos++;
            }else{
                playerInventory.setItem(pos, is);
                pos++;
            }
        }
        if(armor[0] == null){
            playerInventory.setHelmet(null);
        }else{
            playerInventory.setHelmet(armor[0]);
        }
        if(armor[1] == null){
            playerInventory.setChestplate(null);
        }else{
            playerInventory.setChestplate(armor[1]);
        }
        if(armor[2] == null){
            playerInventory.setLeggings(null);
        }else{
            playerInventory.setLeggings(armor[2]);
        }
        if(armor[3] == null){
            playerInventory.setBoots(null);
        }else{
            playerInventory.setBoots(armor[3]);
        }

        return true;
    }


    private String playerInventoryToString (Player player) {
        return inventoryToBase64(player.getInventory());
    }


    private String playerArmorToString (Player player) {
        ItemStack[] armor = new ItemStack[4];
        if (player.getInventory().getHelmet() != null) {
            armor[0] = player.getInventory().getHelmet();
        }
        if (player.getInventory().getChestplate() != null) {
            armor[1] = player.getInventory().getChestplate();
        }
        if (player.getInventory().getLeggings() != null) {
            armor[2] = player.getInventory().getLeggings();
        }
        if (player.getInventory().getBoots() != null) {
            armor[3] = player.getInventory().getBoots();
        }

        return itemStackArrayToBase64(armor);
    }


    private Inventory inventoryFromBase64 (String data) throws IOException {
        if (data == null) {
            return Bukkit.getServer().createInventory(null, 36);
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int trueInvSize = dataInput.readInt();
            int invSize = trueInvSize;
            invSize = invSize / 9;
            invSize = invSize * 9;
            invSize = invSize + 9;
            Inventory inventory = Bukkit.getServer().createInventory(null, invSize);

            // Read the serialized inventory
            for (int i = 0; i < trueInvSize; i++) {
                ItemStack itemStack = (ItemStack) dataInput.readObject();
                if (itemStack != null
                        && itemStack.hasItemMeta()
                        && itemStack.getItemMeta().hasDisplayName()) {
                    String newDisplayName = fixItemName(itemStack.getItemMeta().getDisplayName());
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    itemMeta.setDisplayName(newDisplayName);
                    itemStack.setItemMeta(itemMeta);
                }
                inventory.setItem(i, itemStack);
            }

            dataInput.close();
            return inventory;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }


    private ItemStack[] itemStackArrayFromBase64 (String data) throws IOException {
        if (data == null) {
            ItemStack[] items = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                items[i] = new ItemStack(Material.AIR);
            }
            return items;
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];

            // Read the serialized inventory
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            for (int i = 0; i < items.length; i++) {
                ItemStack itemStack = items[i];
                if (itemStack != null
                        && itemStack.hasItemMeta()
                        && itemStack.getItemMeta().hasDisplayName()) {
                    String newDisplayName = fixItemName(itemStack.getItemMeta().getDisplayName());
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    itemMeta.setDisplayName(newDisplayName);
                    itemStack.setItemMeta(itemMeta);
                }
            }

            dataInput.close();
            return items;
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }

    public String fixItemName (String oldName) {
        char c = (char) Integer.parseInt("0378", 16);
        String newName = oldName.replace(c + "", ChatColor.RESET + "" + ChatColor.BLACK + "" + ChatColor.RESET + "");
        return newName;
    }


    private String itemStackArrayToBase64 (ItemStack[] items) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeInt(items.length);

            // Save every element in the list
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            // Serialize that array
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }


    private String inventoryToBase64 (Inventory inventory) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the inventory
            dataOutput.writeInt(inventory.getSize());

            // Save every element in the list
            for (int i = 0; i < inventory.getSize(); i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            // Serialize that array
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }
}
