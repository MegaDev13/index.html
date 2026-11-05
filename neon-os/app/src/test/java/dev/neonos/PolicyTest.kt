package dev.neonos
import org.junit.Assert.*
import org.junit.Test
class PolicyTest {
    @Test fun effectsAreDistinct() {assertEquals(5,Policy.effects.toSet().size)}
    @Test fun durationsStayWithinBudget() {for(n in -100..1000)assertTrue(Policy.duration(n,false,false,true) in 200..350)}
    @Test fun instantAndSaverRespectSystem() {assertEquals(0L,Policy.duration(350,true,false,true));assertEquals(0L,Policy.duration(350,false,true,true));assertEquals(0L,Policy.duration(350,false,false,false))}
    @Test fun searchIgnoresCaseAndAccents() {assertTrue(Policy.matches("Configurações","CONFIGURACOES"));assertFalse(Policy.matches("Câmera","banco"))}
    @Test fun noEffectWithoutObservedLock() {val gate=UnlockGate();gate.userPresent(5);assertFalse(gate.consume(6,false))}
    @Test fun onlyOneEffectPerObservedUnlock() {val gate=UnlockGate();gate.observeLocked();gate.userPresent(100);assertTrue(gate.consume(110,false));assertFalse(gate.consume(120,false))}
    @Test fun lockedAndStaleEventsNeverPlay() {val gate=UnlockGate();gate.observeLocked();gate.userPresent(100);assertFalse(gate.consume(101,true));assertFalse(gate.consume(5000,false))}
    @Test fun publicKeyguardTransitionWorksWithoutBroadcast() {val gate=UnlockGate();gate.observeLocked();gate.visibleUnlocked(100);assertTrue(gate.consume(110,false));gate.visibleUnlocked(120);assertFalse(gate.consume(130,false))}
    @Test fun freshUnlockExpires() {val gate=UnlockGate();gate.observeLocked();gate.userPresent(100);assertFalse(gate.consume(2700,false))}
}
