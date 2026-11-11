package dev.serverworks;

/** Client-only actions that common code may trigger. The client fills these in at start-up. */
public final class ClientHooks {
	/** Opens the Operator's Manual. Does nothing on a dedicated server. */
	public static Runnable openManual = () -> { };
	/** Opens the Config Card editor for the card in this hand. */
	public static java.util.function.Consumer<net.minecraft.world.InteractionHand> editConfigCard = hand -> { };
	/** Opens the World Drive editor for the drive in this hand. */
	public static java.util.function.Consumer<net.minecraft.world.InteractionHand> editWorldDrive = hand -> { };

	private ClientHooks() {
	}
}
