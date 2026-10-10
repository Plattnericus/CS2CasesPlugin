package dev.plattnericus.cases.tools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.*;
import dev.plattnericus.cases.command.*;
import dev.plattnericus.cases.core.*;
import dev.plattnericus.cases.profile.EquipSlot;
import dev.plattnericus.cases.skin.SkinInstance;
import dev.plattnericus.cases.storage.Database;
import dev.plattnericus.cases.util.Text;
import io.papermc.paper.command.brigadier.*;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Live production handlers/services/SQL; only message audiences and selector arguments are wrapped. */
public final class CommandRuntimeChecks {
    private final Plugin plugin;
    private final CasesContext ctx;
    private final Player player, audience;
    private final CommandSender reporter;
    private final List<String> messages=new ArrayList<>(), passed=new ArrayList<>();
    private final Map<String,BasicCommand> basics=new LinkedHashMap<>();
    private final Set<String> denied=new HashSet<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher=new CommandDispatcher<>();
    private LiteralCommandNode<CommandSourceStack> admin;
    private SkinInstance knife,gun;

    private CommandRuntimeChecks(Plugin plugin,CommandSender reporter,Player player,CasesContext ctx) {
        this.plugin=plugin;this.reporter=reporter;this.player=player;this.ctx=ctx;
        audience=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},(proxy,m,a)->{
            if(m.getName().equals("sendMessage")){capture(a);return null;}
            if(m.getName().equals("hasPermission")&&a[0] instanceof String p&&denied.contains(p))return false;
            try{return m.invoke(player,a);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    public static void run(Plugin p,CommandSender s,Player player,CasesContext ctx){new CommandRuntimeChecks(p,s,player,ctx).run();}
    @SuppressWarnings("unchecked")
    private void register(){
        Commands registrar=(Commands)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Commands.class},(proxy,m,a)->{
            if(!m.getName().equals("register"))throw new UnsupportedOperationException(m.getName());
            if(a[0] instanceof String name){BasicCommand c=(BasicCommand)a[a.length-1];basics.put(name,c);for(String alias:(Collection<String>)a[2])basics.put(alias,c);}
            else admin=(LiteralCommandNode<CommandSourceStack>)a[0];return Set.of();
        });
        PlayerCommands.register(registrar,ctx);new AdminCommand((CasesRuntime)ctx).register(registrar);dispatcher.getRoot().addChild(admin);
    }
    private CommandSourceStack source(boolean console){
        return(CommandSourceStack)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CommandSourceStack.class},(p,m,a)->switch(m.getName()){
            case "getSender"->audience;case "getExecutor"->console?null:audience;case "getLocation"->player.getLocation();
            case "getPlayerOrThrow","getEntityOrThrow"->audience;case "withExecutor","withLocation"->p;
            default->throw new UnsupportedOperationException(m.getName());
        });
    }
    private void capture(Object[]args){for(Object a:args){if(a instanceof Component c)messages.add(Text.plain(c));else if(a instanceof String s)messages.add(s);else if(a instanceof Object[]nested)capture(nested);}}
    private boolean says(String word){return messages.stream().anyMatch(s->s.contains(word));}
    private void require(boolean ok,String description){if(!ok)throw new AssertionError(description+"; replies="+messages);}
    private void pass(String text){passed.add(text);plugin.getLogger().info("PASS COMMAND: "+text);}
    private void reset(){messages.clear();ctx.gallery().close(player);ctx.inspect().stop(player);ctx.previews().end(player,false);player.closeInventory();}
    private CompletableFuture<Void> later(int ticks){var f=new CompletableFuture<Void>();Bukkit.getScheduler().runTaskLater(plugin,()->f.complete(null),ticks);return f;}
    private CompletableFuture<Void> until(BooleanSupplier ready){
        var f=new CompletableFuture<Void>();new org.bukkit.scheduler.BukkitRunnable(){int ticks;public void run(){
            try{if(ready.getAsBoolean()){cancel();f.complete(null);}else if((ticks+=2)>400){cancel();f.completeExceptionally(new AssertionError("Command timeout; replies="+messages));}}
            catch(Throwable e){cancel();f.completeExceptionally(e);}
        }}.runTaskTimer(plugin,2,2);return f;
    }
    private CompletableFuture<Void> main(CompletableFuture<?> f){var n=new CompletableFuture<Void>();f.whenComplete((v,e)->Bukkit.getScheduler().runTask(plugin,()->{if(e==null)n.complete(null);else n.completeExceptionally(e);}));return n;}
    private void basic(String name,boolean console,String...args){try{basics.get(name).execute(source(console),args);}catch(Exception e){throw new IllegalStateException(name,e);}}
    private void admin(String path,Map<String,Object>args,boolean console){
        CommandNode<CommandSourceStack> node=admin;
        if(!path.isEmpty())for(String part:path.split(" "))node=node.getChild(part);
        if(node==null||node.getCommand()==null)throw new AssertionError("Missing admin branch "+path);
        var builder=new CommandContextBuilder<>(dispatcher,source(console),admin,0);
        args.forEach((k,v)->builder.withArgument(k,new ParsedArgument<>(0,1,v)));
        try{node.getCommand().run(builder.build("csadmin "+path));}catch(Exception e){throw new IllegalStateException(path,e);}
    }
    private Map<String,Object>target(String...pairs){var a=new HashMap<String,Object>();a.put("player",(PlayerSelectorArgumentResolver)s->List.of(player));for(int i=0;i<pairs.length;i+=2)a.put(pairs[i],pairs[i+1]);return a;}
    private Map<String,Object>value(SkinInstance skin,Object val){var a=target("id",skin.shortId());a.put("value",val);return a;}
    private CompletableFuture<Void>check(String path,Map<String,Object>a,boolean console,BooleanSupplier ready){reset();admin(path,a,console);return until(ready).thenRun(()->pass("/csadmin "+path));}
    private CompletableFuture<Void>bad(String name,String expected,String...a){reset();basic(name,false,a);return later(2).thenRun(()->{require(says(expected),"Missing rejection /"+name+Arrays.toString(a));pass("error /"+name+Arrays.toString(a));});}
    private CompletableFuture<Void>menu(String command,Class<?> type){reset();require(Bukkit.dispatchCommand(player,command),"Unregistered "+command);return until(()->type.isInstance(player.getOpenInventory().getTopInventory().getHolder(false))).thenRun(()->pass("live menu /"+command));}
    private Database database(){try{var f=ctx.repository().getClass().getDeclaredField("db");f.setAccessible(true);return(Database)f.get(ctx.repository());}catch(Exception e){throw new IllegalStateException(e);}}
    private CompletableFuture<Void>sql(String text){return main(database().run(c->{try(var s=c.createStatement()){s.execute(text);}return null;}));}

    private void run(){
        register();require(ctx.profiles().get(player)!=null,"Profile unavailable");require(basics.size()==14,"Missing alias");
        CompletableFuture<Void>flow=CompletableFuture.completedFuture(null);
        for(String name:basics.keySet())flow=flow.thenRun(()->{
            reset();require(Bukkit.dispatchCommand(player,name),"Unregistered /"+name);pass("registered /"+name);
            reset();basic(name,true);require(says("Players only"),"Missing console rejection "+name);pass("console /"+name);
            reset();denied.add(basics.get(name).permission());basic(name,false);denied.clear();require(says("permission"),"Missing permission rejection "+name);pass("permission /"+name);
        }).thenCompose(v->later(4));
        String[][]invalid={
            {"inspect","Invalid command","wrong"},{"inspect","Invalid command","hand","extra"},
            {"inventory","Invalid command","vanilla","extra"},{"knife","Invalid command","vanilla","extra"},
            {"tradein","Invalid command","extra"},{"tradeup","Invalid command","extra"},{"openings","Invalid command","extra"},
            {"cases","Invalid command","open","kilowatt_case","0"},{"cases","Invalid command","open","kilowatt_case","1001"},
            {"cases","Invalid command","open","kilowatt_case","nope"},{"cases","Unknown case","open","bad_case","1"},
            {"cases","Invalid command","cancel","extra"},{"trade","Invalid command","cancel","extra"},{"trade","Invalid command","decline","extra"},
            {"trade","Invalid command","accept","a","b"},{"trade","No matching trade request","cancel"},
            {"trade","No matching trade request","accept"},{"trade","No matching trade request","decline"},{"trade","cannot trade","DefinitelyMissingPlayer"},
            {"market","Invalid command","balance","extra"},{"market","Invalid command","claims","extra"},
            {"market","Invalid command","own","extra"},{"market","Invalid command","recover","extra"},
            {"market","Invalid command","search"},{"market","Invalid command","sell","a"},
            {"market","No matching skin","sell","missing-id","1"},{"market","Invalid command","unknown"}};
        for(var row:invalid)flow=flow.thenCompose(v->bad(row[0],row[1],Arrays.copyOfRange(row,2,row.length)));
        flow=flow.thenRun(()->{
            var other=Bukkit.getOnlinePlayers().stream().filter(p->!p.getUniqueId().equals(player.getUniqueId())).findFirst().orElseThrow();
            reset();ctx.commerce().request(player,other);ctx.commerce().accept(other,player.getName());
            var trade=ctx.commerce().trade(player.getUniqueId());require(trade!=null,"Trade fixture missing");
            try{trade.toggle(player.getUniqueId(),UUID.randomUUID());long reviewed=System.currentTimeMillis()+11000;require(trade.confirm(player.getUniqueId(),reviewed)&&trade.confirm(other.getUniqueId(),reviewed),"Trade fixture confirmations missing");trade.beginCommit();basic("trade",false,"accept");require(says("already in progress"),"Committing trade acceptance gave no error");pass("committing trade acceptance feedback");}
            finally{try{var end=ctx.commerce().getClass().getDeclaredMethod("end",dev.plattnericus.cases.commerce.TradeSession.class);end.setAccessible(true);end.invoke(ctx.commerce(),trade);}catch(Exception e){throw new IllegalStateException(e);}player.closeInventory();other.closeInventory();}
        });
        flow=flow.thenRun(()->{reset();var broken=CommandFeedback.guard(ctx,"audit-injected",new BasicCommand(){public void execute(CommandSourceStack s,String[]a){throw new IllegalStateException("EXPECTED command audit injection");}});
            try{broken.execute(source(false),new String[0]);}catch(Exception e){throw new IllegalStateException(e);}
            require(says("reference")&&!says("EXPECTED"),"Missing reference or exception details leaked");pass("synchronous exception guard");
        }).thenCompose(v->{reset();CommandFeedback.complete(ctx,audience,"audit-injected-async",CompletableFuture.failedFuture(new IllegalStateException("EXPECTED async audit injection")),x->{});return until(()->says("reference"));})
          .thenRun(()->pass("asynchronous exception guard"));
        flow=flow.thenCompose(v->check("",Map.of(),true,()->!messages.isEmpty()));
        flow=flow.thenCompose(v->check("info",Map.of(),true,()->says("815")));
        flow=flow.thenCompose(v->check("givecase player case",target("case","kilowatt_case"),true,()->says("Gave")));
        flow=flow.thenCompose(v->check("givekey player key",target("key","case_key"),true,()->says("Gave")));
        flow=flow.thenCompose(v->{var a=target("skin","butterfly_doppler");a.put("float",.03);a.put("pattern",10);a.put("stattrak",true);return check("giveskin player skin float pattern stattrak",a,true,()->says("Gave"));})
            .thenRun(()->knife=ctx.profiles().get(player).owned().stream().filter(s->s.skinId().equals("butterfly_doppler")).findFirst().orElseThrow());
        flow=flow.thenCompose(v->check("giveskin player skin",target("skin","ak47_case_hardened"),true,()->says("Gave")))
            .thenRun(()->gun=ctx.profiles().get(player).owned().stream().filter(s->s.skinId().equals("ak47_case_hardened")).findFirst().orElseThrow());
        flow=flow.thenCompose(v->check("list player",target(),true,()->says(knife.shortId())));
        flow=flow.thenCompose(v->check("removeskin player id",target("id",knife.shortId().substring(0,1)),true,()->says("No matching skin")))
            .thenRun(()->{require(ctx.profiles().get(player).get(knife.id())==knife&&!says("Done."),"Short ambiguous ID removed a skin");pass("unsafe skin ID prefix rejected");});
        flow=flow.thenCompose(v->check("history player",target(),true,()->!messages.isEmpty()));
        flow=flow.thenCompose(v->check("odds case",Map.of("case","kilowatt_case"),true,()->says("%")));
        flow=flow.thenCompose(v->check("equip player id",target("id",knife.shortId()),true,()->says("Done.")));
        flow=flow.thenCompose(v->check("equipslot player id slot",target("id",gun.shortId(),"slot","crossbow"),true,()->says("Done.")));
        flow=flow.thenCompose(v->check("setfloat player id value",value(knife,.9),true,()->says("requires a float")))
            .thenRun(()->{require(knife.floatValue()!=.9&&!says("Done."),"Invalid skin float was accepted");pass("skin float range rejection");});
        flow=flow.thenCompose(v->{var def=ctx.catalog().skins().stream().filter(s->!s.weapon().statTrak()).findFirst().orElseThrow();var a=target("skin",def.id());a.put("float",def.minFloat());a.put("pattern",10);a.put("stattrak",true);return check("giveskin player skin float pattern stattrak",a,true,()->says("StatTrak"));})
            .thenRun(()->{require(!says("Gave"),"Unsupported StatTrak was granted");pass("unsupported StatTrak rejection");});
        for(String mode:List.of("","hand","view"))flow=flow.thenRun(this::reset).thenCompose(v->later(ctx.settings().inspect().cooldownTicks()+2)).thenRun(()->{messages.clear();basic("inspect",false,mode.isEmpty()?new String[0]:new String[]{mode});require(ctx.inspect().isInspecting(player),"Inspect did not start: "+mode);pass("inspect "+mode);});
        flow=flow.thenRun(()->{reset();basic("inspect",false);require(says("cooldown"),"Missing cooldown error");pass("inspect cooldown feedback");});
        flow=flow.thenCompose(v->check("setfloat player id value",value(knife,.05),true,()->says("Done.")&&knife.floatValue()==.05));
        flow=flow.thenCompose(v->check("setpattern player id value",value(knife,661),true,()->says("Done.")&&knife.pattern()==661));
        flow=flow.thenCompose(v->check("setstattrak player id value",value(knife,false),true,()->says("Done.")&&!knife.statTrak()));
        flow=flow.thenRun(()->{reset();admin("equipslot player id slot",target("id",knife.shortId(),"slot","bow"),true);require(says("slot")&&!says("Done."),"Invalid slot gave no error or false success");pass("invalid slot feedback to invoking admin");});
        flow=flow.thenCompose(v->check("manage name",Map.of("name",player.getName()),false,()->player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.gui.menu.SkinInventoryMenu));
        flow=flow.thenCompose(v->check("preview skin",Map.of("skin","butterfly_doppler"),false,()->ctx.previews().isPreviewing(player)));
        flow=flow.thenCompose(v->check("pattern skin pattern",Map.of("skin","ak47_case_hardened","pattern",661),true,()->says("Classification")));
        flow=flow.thenCompose(v->check("browser skin",Map.of("skin","ak47_case_hardened"),false,()->player.getOpenInventory().getTopInventory().getHolder(false) instanceof dev.plattnericus.cases.admin.PatternBrowserMenu));
        flow=flow.thenCompose(v->menu("market",dev.plattnericus.cases.commerce.MarketMenu.class))
            .thenCompose(v->menu("market own",dev.plattnericus.cases.commerce.MarketMenu.class))
            .thenCompose(v->menu("market search doppler",dev.plattnericus.cases.commerce.MarketMenu.class))
            .thenCompose(v->menu("market sell",dev.plattnericus.cases.commerce.SkinPickerMenu.class))
            .thenCompose(v->menu("market sell "+gun.shortId()+" 64",dev.plattnericus.cases.commerce.SellMenu.class))
            .thenCompose(v->menu("trade",dev.plattnericus.cases.commerce.TradePlayersMenu.class))
            .thenCompose(v->menu("cases",dev.plattnericus.cases.gui.menu.CasesMenu.class))
            .thenCompose(v->menu("openings",dev.plattnericus.cases.opening.ActiveOpeningsMenu.class))
            .thenCompose(v->menu("tradein",dev.plattnericus.cases.tradein.TradeInMenu.class));
        flow=flow.thenCompose(v->check("scan skin",Map.of("skin","ak47_case_hardened"),true,()->says("playside.blue")));
        flow=flow.thenCompose(v->check("scan skin metric count",Map.of("skin","ak47_case_hardened","metric","playside.blue","count",2),true,()->says("1.")));
        for(String type:List.of("","villager","mannequin"))flow=flow.thenCompose(v->check("shop spawn"+(type.isEmpty()?"":" "+type),Map.of(),false,()->!messages.isEmpty()))
            .thenCompose(v->check("shop remove",Map.of(),false,()->!messages.isEmpty()));
        flow=flow.thenCompose(v->check("exportpack",Map.of(),true,()->says("skins exported")));
        flow=flow.thenCompose(v->check("reload",Map.of(),true,()->says("Reloaded in")));
        flow=flow.thenCompose(v->badReload());
        flow=flow.thenCompose(v->faults());
        flow=flow.thenRun(()->{reset();require(Bukkit.dispatchCommand(player,"cases open kilowatt_case 1"),"Queue command missing");})
            .thenCompose(v->until(()->ctx.openings().isOpening(player))).thenCompose(v->until(()->!ctx.openings().isOpening(player))).thenRun(()->pass("live /cases open and reward completion"));
        for(boolean keep:List.of(false,true))flow=flow.thenCompose(v->{reset();admin("testcase case"+(keep?" keep":""),Map.of("case","kilowatt_case"),false);return until(()->ctx.openings().isOpening(player));})
            .thenCompose(v->until(()->!ctx.openings().isOpening(player))).thenRun(()->pass("testcase "+(keep?"keep":"temporary")));
        flow=flow.thenCompose(v->check("removeskin player id",target("id",gun.shortId()),true,()->says("Done.")&&ctx.profiles().get(player).get(gun.id())==null));
        flow.whenComplete((v,e)->{reset();try{Files.createDirectories(plugin.getDataFolder().toPath());Files.writeString(plugin.getDataFolder().toPath().resolve("command-audit.txt"),String.join("\n",passed)+"\n"+(e==null?"PASS ALL "+passed.size()+" COMMAND CHECKS":"FAIL "+e)+"\n");}catch(Exception x){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save audit",x);return;}
            if(e!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"COMMAND AUDIT FAILED",e);
            else{String result="PASS ALL "+passed.size()+" COMMAND CHECKS: roots/aliases, live handlers, invalid input, permissions, console, SQL rollback and error references.";reporter.sendMessage(result);plugin.getLogger().info(result);}});
    }
    private CompletableFuture<Void>faults(){
        String skins=database().table("skins"),equipped=database().table("equipped");double original=knife.floatValue();
        CompletableFuture<Void>flow=sql("CREATE TRIGGER command_audit_update BEFORE UPDATE ON "+skins+" BEGIN SELECT RAISE(ABORT, 'EXPECTED command audit SQL update'); END");
        flow=flow.thenCompose(v->check("setfloat player id value",value(knife,.04),true,()->says("reference")))
            .thenRun(()->{require(knife.floatValue()==original&&!says("Done."),"Failed update mutated profile");pass("SQL update failure preserves memory");})
            .thenCompose(v->check("removeskin player id",target("id",knife.shortId()),true,()->says("reference")))
            .thenRun(()->{require(ctx.profiles().get(player).get(knife.id())==knife&&knife.status()==SkinInstance.Status.OWNED&&!says("Done."),"Failed deletion removed skin");pass("SQL deletion failure preserves skin");})
            .thenCompose(v->sql("DROP TRIGGER command_audit_update"));
        flow=flow.thenCompose(v->sql("CREATE TRIGGER command_audit_equipped BEFORE INSERT ON "+equipped+" BEGIN SELECT RAISE(ABORT, 'EXPECTED command audit equipment'); END"))
            .thenCompose(v->{reset();admin("equipslot player id slot",target("id",gun.shortId(),"slot","bow"),false);require(!ctx.knives().equip(player,knife),"Concurrent equip was accepted");pass("concurrent equipment rejected");return until(()->says("could not be equipped")&&gun.id().equals(ctx.profiles().get(player).equipped(EquipSlot.CROSSBOW)));})
            .thenRun(()->{require(ctx.profiles().get(player).equipped(EquipSlot.BOW)==null&&gun.id().equals(ctx.profiles().get(player).equipped(EquipSlot.CROSSBOW))&&!says("Done."),"Equipment failure did not restore slots");pass("SQL equipment failure restores slots");})
            .thenCompose(v->main(database().run(c->{try(var s=c.prepareStatement("SELECT slot FROM "+equipped+" WHERE owner=? AND instance_id=?")){s.setString(1,player.getUniqueId().toString());s.setString(2,gun.id().toString());try(var rows=s.executeQuery()){require(rows.next()&&rows.getString(1).equals("crossbow")&&!rows.next(),"Equipment transaction lost previous SQL slot");}}return null;})))
            .thenRun(()->pass("SQL equipment rollback preserves persistent slot"))
            .thenCompose(v->sql("DROP TRIGGER command_audit_equipped"));
        flow=flow.thenCompose(v->sql("CREATE TRIGGER command_audit_insert BEFORE INSERT ON "+skins+" BEGIN SELECT RAISE(ABORT, 'EXPECTED command audit insert'); END"))
            .thenCompose(v->{int count=ctx.profiles().get(player).owned().size();return check("giveskin player skin",target("skin","ak47_case_hardened"),true,()->says("reference"))
                .thenRun(()->{require(ctx.profiles().get(player).owned().size()==count&&!says("Gave"),"Failed grant added skin");pass("SQL grant failure preserves collection");});})
            .thenCompose(v->sql("DROP TRIGGER command_audit_insert"));return flow;
    }
    private CompletableFuture<Void>badReload(){
        var file=ctx.plugin().getDataFolder().toPath().resolve("config.yml");String saved;
        try{saved=Files.readString(file);Files.writeString(file,"inspect: [invalid yaml\n");}catch(Exception e){throw new IllegalStateException(e);}
        var catalog=ctx.catalog();
        return check("reload",Map.of(),true,()->says("Reload failed")).thenRun(()->{require(ctx.catalog()==catalog&&!says("Reloaded in"),"Failed reload replaced active configuration");pass("invalid config reload preserves previous catalog");})
            .whenComplete((v,e)->{try{Files.writeString(file,saved);}catch(Exception x){throw new IllegalStateException(x);}});
    }
}
