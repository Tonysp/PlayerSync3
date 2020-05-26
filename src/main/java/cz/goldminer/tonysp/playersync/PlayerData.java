package cz.goldminer.tonysp.playersync;

import java.io.Serializable;

public class PlayerData implements Serializable {
    public String name;
    public String inventory;
    public String armor;
    public int level;

    public PlayerData(String name, String inventory, String armor, int level){
        this.name = name;
        this.inventory = inventory;
        this.armor = armor;
        this.level = level;
    }

    public String getName(){
        return this.name;
    }

    public String getInventory(){ return this.inventory; }

    public String getArmor(){ return this.armor; }

    public int getLevel(){
        return this.level;
    }
}
