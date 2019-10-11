package mc.alk.battlecore.executor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import mc.alk.battlecore.util.Log;
import mc.alk.battlecore.util.StringUtil;
import mc.alk.mc.ChatColor;
import mc.alk.mc.MCOfflinePlayer;
import mc.alk.mc.MCPlatform;
import mc.alk.mc.MCPlayer;
import mc.alk.mc.command.MCCommandExecutor;
import mc.alk.mc.command.MCCommandSender;
import mc.alk.mc.command.MCConsoleCommandSender;

public class CustomCommandExecutor implements MCCommandExecutor {
	public static final String version = "2.1.0";
	static final boolean DEBUG = false;

	private HashMap<String,TreeMap<Integer,MethodWrapper>> methods =
			new HashMap<String,TreeMap<Integer,MethodWrapper>>();
	private HashMap<String,Map<String,TreeMap<Integer,MethodWrapper>>> subCmdMethods =
			new HashMap<String,Map<String,TreeMap<Integer,MethodWrapper>>>();

	protected PriorityQueue<MethodWrapper> usage = new PriorityQueue<MethodWrapper>(2, new Comparator<MethodWrapper>(){
		public int compare(MethodWrapper mw1, MethodWrapper mw2) {
			MCCommand cmd1 = mw1.getCommand();
			MCCommand cmd2 = mw2.getCommand();

			int c = new Float(cmd1.helpOrder()).compareTo(cmd2.helpOrder());
			if (c!=0) return c;
			c = new Integer(cmd1.order()).compareTo(cmd2.order());
			return c != 0 ? c : new Integer(cmd1.hashCode()).compareTo(cmd2.hashCode());
		}
	});
	static final String DEFAULT_CMD = "_dcmd_";

	/**
	 * Custom arguments class so that we can return a modified arguments
	 */
	public static class Arguments{
		public Object[] args;
	}

	protected static class MethodWrapper{
		public MethodWrapper(Object obj, Method method){
			this.obj = obj; this.method = method;
		}

		public Object obj; /// Object instance the method belongs to
		public Method method; /// Method
		public String usage;

		public MCCommand getCommand(){
			return this.method.getAnnotation(MCCommand.class);
		}
	}

	/**
	 * When no arguments are supplied, no method is found
	 * What to display when this happens
	 * @param sender
	 */
	protected void showHelp(MCCommandSender sender, mc.alk.mc.command.MCCommand command){
		showHelp(sender,command,null);
	}
	protected void showHelp(MCCommandSender sender, mc.alk.mc.command.MCCommand command, String[] args){
		help(sender,command,args);
	}

	protected CustomCommandExecutor(){
		addMethods(this, getClass().getMethods());
	}

	protected boolean validCommandSenderClass(Class<?> clazz){
		return clazz != MCCommandSender.class || clazz != MCPlayer.class;
	}

	public void addMethods(Object obj, Method[] methodArray){

		for (Method method : methodArray){
			MCCommand mc = method.getAnnotation(MCCommand.class);
			if (mc == null)
				continue;
			Class<?> types[] = method.getParameterTypes();
			if (types.length == 0 || !validCommandSenderClass(types[0])){
				System.err.println("MCCommands must start with a CommandSender,Player, or ArenaPlayer");
				continue;
			}
			if (mc.cmds().length == 0){ /// There is no subcommand. just the command itself with arguments
				addMethod(obj, method, mc, DEFAULT_CMD);
			} else {
				/// For each of the cmds, store them with the method
				for (String cmd : mc.cmds()){
					addMethod(obj, method, mc, cmd.toLowerCase());}
			}
		}
	}

	private void addMethod(Object obj, Method method, MCCommand mc, String cmd) {
		int ml = method.getParameterTypes().length;
		if (mc.subCmds().length == 0){
			TreeMap<Integer,MethodWrapper> mthds = methods.get(cmd);
			if (mthds == null){
				mthds = new TreeMap<Integer,MethodWrapper>();
			}
			int order = (mc.order() != -1? mc.order()*100000 :Integer.MAX_VALUE) - ml*100 - mthds.size();
			MethodWrapper mw = new MethodWrapper(obj,method);
			mthds.put(order, mw);
			methods.put(cmd, mthds);
			addUsage(mw, mc);
		} else {
			Map<String,TreeMap<Integer,MethodWrapper>> basemthds = subCmdMethods.get(cmd);
			if (basemthds == null){
				basemthds = new HashMap<String,TreeMap<Integer,MethodWrapper>>();
				subCmdMethods.put(cmd, basemthds);
			}
			for (String subcmd: mc.subCmds()){
				TreeMap<Integer,MethodWrapper> mthds = basemthds.get(subcmd);
				if (mthds == null){
					mthds = new TreeMap<Integer,MethodWrapper>();
					basemthds.put(subcmd, mthds);
				}
				int order = (mc.order() != -1? mc.order()*100000 :Integer.MAX_VALUE) - ml*100-mthds.size();
				MethodWrapper mw = new MethodWrapper(obj,method);
				mthds.put(order, mw);
				addUsage(mw, mc);
			}
		}
	}
	private void addUsage(MethodWrapper method, MCCommand mc) {
		/// save the usages, for showing help messages
		if (!mc.usage().isEmpty()){
			method.usage = mc.usage();
		} else { /// Generate an automatic usage string
			method.usage = createUsage(method.method);
		}
		usage.add(method);
	}

	private String createUsage(Method method) {
		MCCommand cmd = method.getAnnotation(MCCommand.class);
		StringBuilder sb = new StringBuilder(cmd.cmds().length > 0 ? cmd.cmds()[0] +" " : "");
		int startIndex = 1;
		if (cmd.subCmds().length > 0){
			sb.append(cmd.subCmds()[0] +" ");
			startIndex = 2;
		}
		Class<?> types[] = method.getParameterTypes();
		for (int i=startIndex;i<types.length;i++){
			Class<?> theclass = types[i];
			sb.append(getUsageString(theclass));
		}
		return sb.toString();
	}

	protected String getUsageString(Class<?> clazz) {
		if (MCPlayer.class ==clazz){
			return "<player> ";
		} else if (MCOfflinePlayer.class ==clazz){
			return "<player> ";
		} else if (String.class == clazz){
			return "<string> ";
		} else if (Integer.class == clazz || int.class == clazz){
			return "<int> ";
		} else if (Float.class == clazz || float.class == clazz){
			return "<number> ";
		} else if (Double.class == clazz || double.class == clazz){
			return "<number> ";
		} else if (Short.class == clazz || short.class == clazz){
			return "<int> ";
		} else if (Boolean.class == clazz || boolean.class == clazz){
			return "<true|false> ";
		} else if (String[].class == clazz || Object[].class == clazz){
			return "[string ... ] ";
		}
		return "<string> ";
	}

	public class CommandException{
		final IllegalArgumentException err;
		final MethodWrapper mw;
		public CommandException(IllegalArgumentException err, MethodWrapper mw){
			this.err = err; this.mw = mw;
		}
	}

	public boolean onCommand(MCCommandSender sender, mc.alk.mc.command.MCCommand command, String label, String[] args) {
		TreeMap<Integer,MethodWrapper> methodmap = null;

		/// No method to handle, show some help
		if ((args.length == 0 && !methods.containsKey(DEFAULT_CMD))
				|| (args.length > 0 && (args[0].equals("?") || args[0].equals("help")))){
			showHelp(sender, command,args);
			return true;
		}
		final int length = args.length;
		final String cmd = length > 0 ? args[0].toLowerCase() : null;
		final String subcmd = length > 1 ? args[1].toLowerCase() : null;
		int startIndex = 0;

		/// check for subcommands
		if (subcmd!=null && subCmdMethods.containsKey(cmd) && subCmdMethods.get(cmd).containsKey(subcmd)){
			methodmap = subCmdMethods.get(cmd).get(subcmd);
			startIndex = 2;
		}
		if (methodmap == null && cmd != null){ /// Find our method, and verify all the annotations
			methodmap = methods.get(cmd);
			if (methodmap != null)
				startIndex =1;
		}

		if (methodmap == null){ /// our last attempt
			methodmap = methods.get(DEFAULT_CMD);
		}

		if (methodmap == null || methodmap.isEmpty()){
			return sendMessage(sender, "&cThat command does not exist!&6 /"+command.getLabel()+" help &cfor help");}

		MCCommand mccmd = null;
		List<CommandException> errs =null;
		boolean success = false;
		for (MethodWrapper mwrapper : methodmap.values()){

			mccmd = mwrapper.method.getAnnotation(MCCommand.class);
			final boolean isOp = sender == null || sender.isOp() || sender instanceof MCConsoleCommandSender;

			if (mccmd.op() && !isOp || mccmd.admin() && !hasAdminPerms(sender)) /// no op, no pass
				continue;
			Arguments newArgs = null;
			try {
				newArgs= verifyArgs(mwrapper,mccmd,sender,command, label, args, startIndex);
				Object completed = mwrapper.method.invoke(mwrapper.obj,newArgs.args);
				if (completed != null && completed instanceof Boolean){
					success = (Boolean)completed;
					if (!success){
						String usage = mwrapper.usage;
						if (usage != null && !usage.isEmpty()){
							sendMessage(sender, usage);}
					}
				} else {
					success = true;
				}
				break; /// success on one
			} catch (IllegalArgumentException e){ /// One of the arguments wasn't correct, store the message
				if (errs == null)
					errs = new ArrayList<CommandException>();
				errs.add(new CommandException(e,mwrapper));
			} catch (Exception e) { /// Just all around bad
				logInvocationError(e, mwrapper,newArgs);
			}
		}
		/// and handle all errors
		if (!success && errs != null && !errs.isEmpty()){
			HashSet<String> usages = new HashSet<String>();
			for (CommandException e: errs){
				usages.add(ChatColor.GOLD + "/" + command.getLabel() + " " + e.mw.usage + " &c:" + e.err.getMessage());
			}
			for (String msg : usages){
				sendMessage(sender, msg);}
		}
		return true;
	}

	private void logInvocationError(Exception e, MethodWrapper mwrapper, Arguments newArgs) {
		System.err.println("[CustomCommandExecutor Error] "+mwrapper.method +" : " + mwrapper.obj +"  : " + newArgs);
		if (newArgs!=null && newArgs.args != null){
			for (Object o: newArgs.args)
				System.err.println("[Error] object=" + (o!=null ? o.toString() : o));
		}
		System.err.println("[Error] Cause=" + e.getCause());
		if (e.getCause() != null) e.getCause().printStackTrace();
		System.err.println("[Error] Trace Continued ");
		e.printStackTrace();
	}

	public static final String ONLY_INGAME =ChatColor.RED+"You need to be in game to use this command";
	protected Arguments verifyArgs(MethodWrapper mwrapper, MCCommand cmd, MCCommandSender sender,
								   mc.alk.mc.command.MCCommand command, String label, String[] args, int startIndex) throws IllegalArgumentException{
		if (DEBUG){
			Log.info(" method="+mwrapper.method.getName() + " verifyArgs " + cmd +" sender=" +sender+
					", label=" + label+" args="+ Arrays.toString(args));
			for (String arg: args){
				Log.info(" -- arg=" +arg);}
			for (Class<?> t: mwrapper.method.getParameterTypes()){
				Log.info(" -- type=" +t);}
		}
		final int paramLength = mwrapper.method.getParameterTypes().length;

		/// Check our permissions
		if (!cmd.perm().isEmpty() && !sender.hasPermission(cmd.perm()) && !(cmd.admin() && hasAdminPerms(sender)))
			throw new IllegalArgumentException("You don't have permission to use this command");

		/// Verify min number of arguments
		if (args.length < cmd.min()){
			throw new IllegalArgumentException("You need at least "+cmd.min()+" arguments");
		}
		/// Verfiy max number of arguments
		if (args.length > cmd.max()){
			throw new IllegalArgumentException("You need less than "+cmd.max()+" arguments");
		}
		/// Verfiy max number of arguments
		if (cmd.exact()!= -1 && args.length != cmd.exact()){
			throw new IllegalArgumentException("You need exactly "+cmd.exact()+" arguments");
		}
		final boolean isPlayer = sender instanceof MCPlayer;
		final boolean isOp = (isPlayer && sender.isOp()) || sender == null || sender instanceof MCConsoleCommandSender;

		if (cmd.op() && !isOp)
			throw new IllegalArgumentException("You need to be op to use this command");

		if (cmd.admin() && !isOp && (isPlayer && !hasAdminPerms(sender)))
			throw new IllegalArgumentException("You need to be an Admin to use this command");

		Class<?> types[] = mwrapper.method.getParameterTypes();

		//		/// In game check
		if (types[0] == MCPlayer.class && !isPlayer){
			throw new IllegalArgumentException(ONLY_INGAME);
		}
		int strIndex = startIndex/*skip the label*/, objIndex = 1;

		Arguments newArgs = new Arguments(); /// Our return value
		Object[] objs = new Object[paramLength]; /// Our new array of castable arguments

		newArgs.args = objs; /// Set our return object with the new castable arguments
		objs[0] = verifySender(sender, types[0]);
		AtomicInteger numUsedStrings = new AtomicInteger(0);
		for (int i=1;i<types.length;i++){
			Class<?> clazz = types[i];
			try{
				if (MCCommandSender.class == clazz){
					objs[objIndex] = sender;
				} else if (Map.class == clazz) {
					Map<Integer, String> map = new HashMap<Integer, String>();
					int mapIndex = 0;
					for (String s : args) {
						map.put(mapIndex, s);
						mapIndex = mapIndex + 1;
					}
					objs[objIndex] = map;
				} else if (Set.class == clazz) {
					Set<String> set = new HashSet<String>(Arrays.asList(args));
					objs[objIndex] = set;
				} else if (List.class == clazz) {
					List<String> list = Arrays.asList(args);
					objs[objIndex] = list;
				} else if (Collection.class == clazz) {
					Collection<String> c = Arrays.asList(args);
					objs[objIndex] = c;
				} else if (String[].class == clazz){
					objs[objIndex] = args;
				} else if (Object[].class == clazz){
					objs[objIndex] =args;
				} else {
					objs[objIndex] = verifyArg(sender, clazz, command, args, strIndex, numUsedStrings);
					if (objs[objIndex] == null){
						throw new IllegalArgumentException("Argument " + args[strIndex] + " can not be null");
					}
				}
				if (DEBUG)Log.info("   " + objIndex + " : " + strIndex + "  " +
						(args.length > strIndex ? args[strIndex] : null ) + " <-> " + objs[objIndex] +" !!! Cs = " +
						clazz.getCanonicalName());
				if (numUsedStrings.get() > 0){
					strIndex+=numUsedStrings.get();}
			} catch (ArrayIndexOutOfBoundsException e){
				throw new IllegalArgumentException("You didn't supply enough arguments for this method");
			}
			objIndex++;
		}

		/// Verify alphanumeric
		if (cmd.alphanum().length > 0){
			for (int index: cmd.alphanum()){
				if (index >= args.length)
					throw new IllegalArgumentException("String Index out of range. ");
				if (!args[index].matches("[a-zA-Z0-9_]*")) {
					throw new IllegalArgumentException("argument '"+args[index]+"' can only be alphanumeric with underscores");
				}
			}
		}
		return newArgs; /// Success
	}

	protected Object verifyArg(MCCommandSender sender, Class<?> clazz, mc.alk.mc.command.MCCommand command, String[] args, int curIndex, AtomicInteger numUsedStrings) {
		numUsedStrings.set(0);
		if (mc.alk.mc.command.MCCommand.class == clazz) {
			return command;
		}
		String string = args[curIndex];
		if (string == null)
			throw new ArrayIndexOutOfBoundsException();
		numUsedStrings.set(1);
		if (MCPlayer.class == clazz) {
			return verifyPlayer(string);
		} else if (MCOfflinePlayer.class == clazz) {
			return verifyOfflinePlayer(string);
		} else if (String.class == clazz) {
			return string;
		} else if (Integer.class == clazz || int.class == clazz) {
			return verifyInteger(string);
		} else if (Boolean.class == clazz || boolean.class == clazz) {
			return Boolean.parseBoolean(string);
		} else if (Object.class == clazz) {
			return string;
		} else if (Float.class == clazz || float.class == clazz) {
			return verifyFloat(string);
		} else if (Double.class == clazz || double.class == clazz) {
			return verifyDouble(string);
		}
		return null;
	}

	protected Object verifySender(MCCommandSender sender, Class<?> clazz) {
		if (!clazz.isAssignableFrom(sender.getClass())){
			throw new IllegalArgumentException("Sender must be a " + clazz.getSimpleName());}
		return sender;
	}

	protected Object verifyArg(Class<?> clazz, mc.alk.mc.command.MCCommand command, String string, AtomicBoolean usedString) {
		if (mc.alk.mc.command.MCCommand.class == clazz){
			usedString.set(false);
			return command;
		}
		if (string == null)
			throw new ArrayIndexOutOfBoundsException();
		usedString.set(true);
		if (MCPlayer.class ==clazz){
			return verifyPlayer(string);
		} else if (MCOfflinePlayer.class ==clazz){
			return verifyOfflinePlayer(string);
		} else if (String.class == clazz){
			return string;
		} else if (Integer.class == clazz || int.class == clazz){
			return verifyInteger(string);
		} else if (Boolean.class == clazz || boolean.class == clazz){
			return Boolean.parseBoolean(string);
		} else if (Object.class == clazz){
			return string;
		} else if (Float.class == clazz || float.class == clazz){
			return verifyFloat(string);
		} else if (Double.class == clazz || double.class == clazz){
			return verifyDouble(string);
		}
		return null;
	}

	private MCOfflinePlayer verifyOfflinePlayer(String name) throws IllegalArgumentException {
		MCOfflinePlayer p = findOfflinePlayer(name);
		if (p == null)
			throw new IllegalArgumentException("Player " + name+" can not be found");
		return p;
	}

	private MCPlayer verifyPlayer(String name) throws IllegalArgumentException {
		MCPlayer p = findPlayer(name);
		if (p == null || !p.isOnline())
			throw new IllegalArgumentException(name+" is not online ");
		return p;
	}

	private Integer verifyInteger(Object object) throws IllegalArgumentException {
		try {
			return Integer.parseInt(object.toString());
		}catch (NumberFormatException e){
			throw new IllegalArgumentException(ChatColor.RED+(String)object+" is not a valid integer.");
		}
	}

	private Float verifyFloat(Object object) throws IllegalArgumentException {
		try {
			return Float.parseFloat(object.toString());
		}catch (NumberFormatException e){
			throw new IllegalArgumentException(ChatColor.RED+(String)object+" is not a valid float.");
		}
	}

	private Double verifyDouble(Object object) throws IllegalArgumentException {
		try {
			return Double.parseDouble(object.toString());
		}catch (NumberFormatException e){
			throw new IllegalArgumentException(ChatColor.RED+(String)object+" is not a valid double.");
		}
	}

	protected boolean hasAdminPerms(MCCommandSender sender){
		return sender.isOp();
	}


	static final int LINES_PER_PAGE = 8;
	public void help(MCCommandSender sender, mc.alk.mc.command.MCCommand command, String[] args){
		Integer page = 1;

		if (args != null && args.length > 1){
			try{
				page = Integer.valueOf(args[1]);
			} catch (Exception e){
				sendMessage(sender, ChatColor.RED+" " + args[1] +" is not a number, showing help for page 1.");
			}
		}

		List<String> available = new ArrayList<String>();
		List<String> unavailable = new ArrayList<String>();
		List<String> onlyop = new ArrayList<String>();

		for (MethodWrapper mw : usage){
			MCCommand cmd = mw.getCommand();
			final String use = "&6/" + command.getLabel() +" " + mw.usage;
			if (cmd.op() && !sender.isOp())
				continue;
			else if (cmd.admin() && !hasAdminPerms(sender))
				continue;
			else if (!cmd.perm().isEmpty() && !sender.hasPermission(cmd.perm()))
				unavailable.add(use);
			else
				available.add(use);
		}
		int npages = available.size()+unavailable.size();
		if (sender.isOp())
			npages += onlyop.size();
		npages = (int) Math.ceil( (float)npages/LINES_PER_PAGE);
		if (page > npages || page <= 0){
			sendMessage(sender, "&4That page doesnt exist, try 1-"+npages);
			return;
		}
		if (command != null && command.getAliases() != null && !command.getAliases().isEmpty()) {
			String aliases = StringUtil.join(command.getAliases(),", ");
			sendMessage(sender, "&eShowing page &6"+page +"/"+npages +"&6 : /"+command.getLabel()+" help <page number>");
			sendMessage(sender, "&e    command &6"+command.getLabel()+"&e has aliases: &6" + aliases);
		} else {
			sendMessage(sender, "&eShowing page &6"+page +"/"+npages +"&6 : /" + command.getLabel() + " help <page number>");
		}
		int i=0;
		for (String use : available){
			i++;
			if (i < (page-1) *LINES_PER_PAGE || i >= page*LINES_PER_PAGE)
				continue;
			sendMessage(sender, use);
		}
		for (String use : unavailable){
			i++;
			if (i < (page-1) *LINES_PER_PAGE || i >= page *LINES_PER_PAGE)
				continue;
			sendMessage(sender, ChatColor.RED+"[Insufficient Perms] " + use);
		}
		if (sender.isOp()){
			for (String use : onlyop){
				i++;
				if (i < (page-1) *LINES_PER_PAGE || i >= page *LINES_PER_PAGE)
					continue;
				sendMessage(sender, ChatColor.AQUA+"[OP only] &6"+use);
			}
		}
	}

	public static boolean sendMessage(MCCommandSender p, String message){
		if (message ==null || message.isEmpty()) return true;
		if (message.contains("\n"))
			return sendMultilineMessage(p,message);
		if (p instanceof MCPlayer){
			if (((MCPlayer) p).isOnline())
				p.sendMessage(colorChat(message));
		} else {
			p.sendMessage(colorChat(message));
		}
		return true;
	}

	public static boolean sendMultilineMessage(MCCommandSender p, String message){
		if (message ==null || message.isEmpty()) return true;
		String[] msgs = message.split("\n");
		for (String msg: msgs){
			if (p instanceof MCPlayer){
				if (((MCPlayer) p).isOnline())
					p.sendMessage(colorChat(msg));
			} else {
				p.sendMessage(colorChat(msg));
			}
		}
		return true;
	}

	public static String colorChat(String msg) {return msg.replace('&', (char) 167);}

	public static MCPlayer findPlayer(String name) {
		if (name == null)
			return null;
		MCPlayer foundPlayer = MCPlatform.getPlatform().getPlayer(name);
		if (foundPlayer != null)
			return foundPlayer;

		for (MCPlayer player : MCPlatform.getPlatform().getOnlinePlayers()) {
			String playerName = player.getName();

			if (playerName.equalsIgnoreCase(name)) {
				foundPlayer = player;
				break;
			}

			if (playerName.toLowerCase().contains(name.toLowerCase())) {
				if (foundPlayer != null) {
					return null;}

				foundPlayer = player;
			}
		}

		return foundPlayer;
	}

	public static MCOfflinePlayer findOfflinePlayer(String name) {
		MCPlayer player = findPlayer(name);
		if (player != null){
			return MCPlatform.getPlatform().getOfflinePlayer(name);
		} else{
			MCOfflinePlayer offlinePlayer = MCPlatform.getPlatform().getOfflinePlayer(name);
			if (offlinePlayer != null)
				return offlinePlayer;

			return null;
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface MCCommand
	{
		/// the cmd and all its aliases, can be blank if you want to do something when they just type
		/// the command only
		String[] cmds() default {};

		/// subCommands
		String[] subCmds() default {};

		/// Verify the number of parameters, inGuild and notInGuild imply min if they have an index > number of args
		int min() default 0;
		int max() default Integer.MAX_VALUE;
		int exact() default -1;

		int order() default -1;
		float helpOrder() default Integer.MAX_VALUE;
		boolean admin() default false; /// admin
		boolean op() default false; /// op

		String usage() default "";
		String usageNode() default "";
		String perm() default ""; /// permission node
		int[] alphanum() default {}; /// only alpha numeric

		boolean selection() default false;	/// Selected arena
		int[] ports() default {};
	}
}