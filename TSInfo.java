import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


class Combine
{
	public int spell;

	public int createsId = 0;
	public String skill = "";
	public String reagents = "";
	public int recipeId = 0;
	public int yield = 0;
	public int itemid = 0;

	public Combine()
	{
		spell = 0;
	}

	public String toString()
	{
		if (skill.length() > 1)
		{
			String temp = "\t[" + spell + "] = \"" + createsId + "|" + skill + "|" + reagents;

			if (itemid != 0)
				return temp + "|" + (recipeId != 0 ? recipeId : "") + "|" + (yield != 0 ? yield : "") + "|" + itemid + "\",\n";

			if (yield != 0)
				return temp + "|" + (recipeId != 0 ? recipeId : "") + "|" + yield + "\",\n";

			if (recipeId != 0)
				return temp + "|" + recipeId + "\",\n";

			return temp + "\",\n";
		}

		return "";
	}
}


class Recipe
{
	public int id;
	public Object result;
	public String source;
	public String price;
	public String factionrank;

	public Recipe(int id, Object result, String source)
	{
		this.id = id;
		this.result = result;
		this.source = source;
		price = "";
		factionrank = "";
	}

	public String toString() {
		return "\t[" + id + "] = \"" + result + "|" + source + "\",";
	}
}


public class TSInfo
{
	public ArrayList combines;
	public Map<Integer, String> components;
	public ArrayList<Object> recipes;
	public ArrayList<Combine> spells;

	public TSInfo()
	{
		combines = new ArrayList();
		components = new HashMap<Integer, String>();
		recipes = new ArrayList<Object>();
		spells = new ArrayList<Combine>();
	}

	public Combine getItem(int id)
	{
		for (Object entry : combines) {
			if (entry instanceof Combine) {
				Combine combine = (Combine)entry;
				if (combine.createsId == id || combine.itemid == id) {
					return combine;
				}
			}
		}
		return null;
	}

	public Combine getSpellItem(int spell)
	{
		for (Object entry : combines) {
			if (entry instanceof Combine) {
				Combine combine = (Combine)entry;
				if (spell > 0 && combine.spell == spell) {
					return combine;
				}
				if (spell < 0 && combine.createsId == spell) {
					return combine;
				}
			}
		}
		return null;
	}

	public void addCombine(Combine newItem)
	{
		int id = newItem.createsId;
		Combine oldItem = getItem(id);
		if (oldItem == null) {
			combines.add(newItem);
			return;
		}
	}

	public int getId(JSONObject obj) throws JSONException
	{
		return obj != null ? obj.getInt("id") : 0;
	}

	public static int getProfessionId(String profession)
	{
		switch (profession) {
			case "Alchemy"        : return 171;
			case "Blacksmithing"  : return 164;
			case "Cooking"        : return 185;
			case "Enchanting"     : return 333;
			case "Engineering"    : return 202;
			case "First Aid"      : return 129;
			case "Inscription"    : return 773;
			case "Jewelcrafting"  : return 755;
			case "Leatherworking" : return 165;
			case "Mining"         : return 186;
			case "Tailoring"      : return 197;
		}
		return 0;
	}

	public static String getProfessionLetter(String profession)
	{
		String temp = "";
		switch (profession) {
			case "Alchemy"        : temp = "A"; break;
			case "Blacksmithing"  : temp = "B"; break;
			case "Cooking"        : temp = "W"; break;
			case "Enchanting"     : temp = "D"; break;
			case "Engineering"    : temp = "E"; break;
			case "First Aid"      : temp = "X"; break;
			case "Inscription"    : temp = "I"; break;
			case "Jewelcrafting"  : temp = "J"; break;
			case "Leatherworking" : temp = "L"; break;
			case "Mining"         : temp = "Y"; break;
			case "Tailoring"      : temp = "T"; break;
		}
		return temp;
	}

	public void scanBuffed(String profession)
	{
		String prefix = "var bt = new Btabs(";
		String suffix = ");bt.init();</script>";
		try
		{
			URL url = new URL("http://wowdata.getbuffed.com/spell/profession/" + getProfessionId(profession));
			URLConnection bc = url.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(bc.getInputStream()));

			spells.clear();
			String line;
			while ((line = in.readLine()) != null) {
				if (line.startsWith(prefix) && line.endsWith(suffix)) {
					combines.add("\n--[[ " + profession + " ]]--\n");
					recipes.add("\n--[[ " + profession + " ]]--");
					JSONArray jArray = new JSONArray(line.substring(prefix.length(), line.length() - suffix.length()));
					if (jArray.length() > 0) {
						JSONObject jObject = jArray.optJSONObject(0);
						if (jObject != null) {
							JSONArray rows = jObject.optJSONArray("rows");
							if (rows != null) {
								for (int i = 0; i < rows.length(); i++) {
									JSONObject row = rows.getJSONObject(i);
									Combine combine = new Combine();
									combine.createsId = getId(row.optJSONObject("p"));
									combine.spell = row.getInt("id");
									spells.add(combine);
								}
							}
						}
					}
					break;
				}
			}
			in.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void scanWowHead(String profession)
	{
		try
		{
			URL url = new URL("http://www.thottbot.com/skill=" + getProfessionId(profession));
			URLConnection bc = url.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(bc.getInputStream()));

			String prefix = "new Listview({template: 'spell', id: 'recipes',";
			String suffix = "});";
			String offset = "data: ";

			spells.clear();
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith(prefix) && line.endsWith(suffix)) {
					combines.add("\n--[[ " + profession + " ]]--\n");
					recipes.add("\n--[[ " + profession + " ]]--");
					int index = line.indexOf(offset);
					if (index != -1) {
						line = line.substring(index + offset.length(), line.length() - suffix.length());
						JSONArray rows = new JSONArray(line);
						for (int i = 0; i < rows.length(); i++) {
							JSONObject row = rows.getJSONObject(i);
							int id = row.optInt("id");

							Combine combine = new Combine();
							combine.spell = id;
							spells.add(combine);
						}
					}
					break;
				}
			}

			in.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}


// Components
// [itemid] = "source"
// Source
//  V = Vendor(5)
//  D = Dropped(2)
//  C = Crafted(1)
//  M = Mined
//  H = Herbalism
//  S = Skinned
//  F = Fished(16)
//  E = Disenchanted
//  G = Gathered
//  P = Pickpocketed(21)

// 1 Crafted
// 2 Dropped
// 4 Quest Reward
// 5 Vendor
// 6 Trainer
// 7 Discovery
// 10 Starter Recipe
// 12 Achievement Reward
// 16 Fished
// 21 Pickpocketed

	public void readProfessionFromWowHead(String profession)
	{
		try
		{
			int current = 0;
			int total = spells.size();

			for (Object temp : spells) {
				if (temp instanceof Combine) {
					Combine combine = (Combine)temp;

//					Thread.sleep(100);

					current = current + 1;
					System.out.println("  Scanning " + current + " of " + total + ". (" + combine.spell + ")\r");

					URL url = new URL("http://www.thottbot.com/spell=" + combine.spell);
					URLConnection bc = url.openConnection();
					InputStreamReader br = new InputStreamReader(bc.getInputStream());
					BufferedReader in = new BufferedReader(br);

					String prefix = "new Listview(";
					String suffix = "});";
					String offset = "data: ";

					combine.skill = getProfessionLetter(profession);

					String line;

					while ((line = in.readLine()) != null) {
						if (line.startsWith(prefix) && line.endsWith(suffix)) {
							int index = line.indexOf(offset);

							if (index != -1) {
								JSONArray rows;
								JSONObject row;

								// recipe data
								if (line.contains("id: 'recipes'")) {
//									System.out.println("'recipes' exists for " + entry.spell);

									line = line.substring(index + offset.length(), line.length() - suffix.length());
									rows = new JSONArray(line);
									row = rows.getJSONObject(0);

									combine.spell = row.getInt("id");

									JSONArray reagents = row.optJSONArray("reagents");
									if (reagents != null) {
										String re = "";
										for (int j = 0; j < reagents.length(); j++) {
											JSONArray obj = reagents.getJSONArray(j);

											int component = obj.getInt(0);
											int amount = obj.getInt(1);

											// TODO: get component source instead of defaulting to "vendor"
											components.put(component, "V");

											re = re + component + ":" + amount + " ";
										}

										combine.reagents = re.trim();
									}

									JSONArray colors = row.optJSONArray("colors");
									if (colors != null) {
										int orange = colors.getInt(0);
										int yellow = colors.getInt(1);
										int green  = colors.getInt(2);
										int grey   = colors.getInt(3);

										if (green == 0) { green = grey; }
										if (yellow == 0) { yellow = green; }
										if (orange == 0) { orange = yellow; }

										combine.skill = combine.skill + orange + "/" + yellow + "/" + green + "/" + grey;
									}

									JSONArray creates = row.optJSONArray("creates");
									if (creates != null) {
										combine.createsId = creates.optInt(0);
										combine.yield = creates.getInt(1);
									}
								}

								// the item that teaches the recipe
								if (line.contains("id: 'taught-by-item'")) {
//									System.out.println("'taught-by-item' exists for " + entry.spell);

									line = line.substring(index + offset.length(), line.length() - suffix.length());
									rows = new JSONArray(line);
									row = rows.getJSONObject(0);

									combine.recipeId = row.optInt("id");

									JSONArray sources = row.optJSONArray("source");
									if (sources != null) {
										String source = "";
										switch (sources.getInt(0)) {
											case 1  : source = "C"; break; // crafted by a player
											case 2  : source = "D"; break; // dropped as loot
											case 4  : source = "Q"; break; // quest reward
											case 5  : source = "V"; break; // bought from vendor
											case 6  : source = "T"; break; // learned from trainer
											case 10 : source = "R"; break; // starter recipe
											case 12 : source = "A"; break; // achievement reward
											case 16 : source = "F"; break; // fished up
											case 21 : source = "P"; break; // pickpocketed
										}
										if (combine.recipeId > 0) {
											recipes.add(new Recipe(combine.recipeId, combine.spell, source));
										}
									}
								}
								addCombine(combine);
							}
//							break;
						}
					}
					in.close();
				}
			}

			System.out.println("  Done!                            \n");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void writeToFile(String filename)
	{
		try
		{
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filename))));

			out.write("\nTradeskillInfo.vars.specialcases = {\n");
//			for (Object item : specialcases) {
//				out.write(item + "\n");
//			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.combines = {\n");
			for (Object combine : combines) {
				out.write(combine + "");
			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.components = {\n");
			for (Map.Entry<Integer, String> entry : components.entrySet())
			{
				out.write("\t[" + entry.getKey() + "] = \"" + entry.getValue() + "\",\n");
			}
			out.write("}\n");

			out.write("\nTradeskillInfo.vars.recipes = {\n");
			for (Object combine : recipes) {
				out.write(combine + "\n");
			}
			out.write("}\n");

			out.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}


	public static void main(String argv[])
	{
		long starttime = System.currentTimeMillis();

		System.out.println("Beginning scan...");
		TSInfo tsi = new TSInfo();

		String[] professions = {
			"Alchemy",
			"Blacksmithing",
			"Cooking",
			"Enchanting",
			"Engineering",
			"First Aid",
			"Inscription",
			"Jewelcrafting",
			"Leatherworking",
			"Mining",
			"Tailoring",
		};

		for (String profession : professions) {
			System.out.println("Scanning " + profession + "...");
			// GetBuffed has enchanting info that Wowhead does not, so let's scrape their website first.
			if (profession.contains("Enchanting")) {
				tsi.scanBuffed(profession);
				tsi.readProfessionFromWowHead(profession);
			} else {
				tsi.scanWowHead(profession);
				tsi.readProfessionFromWowHead(profession);
			}
		}

		System.out.println("Saving data to Data.lua...");
		tsi.writeToFile("Data.lua");

		long difftime = System.currentTimeMillis() - starttime;
		System.out.println("Scan completed in " + difftime / 1000 + " seconds.");
	}
}
