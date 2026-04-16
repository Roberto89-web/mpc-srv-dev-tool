# Modulo Vendite - Personalizzazioni

## Struttura del modulo

Il modulo vendite gestisce ordini, clienti e offerte commerciali.
Le classi principali sono in `com.erp.vendite`.

## Come aggiungere un campo custom

1. Nel tool GUI: vai su **Oggetti > OrdineVendita > Campi** e aggiungi il campo con prefisso `CUSTOM_`
2. Scrivi la personalizzazione in `VenditeCustom.java`:

```java
public class VenditeCustom extends VenditeBase {

    @Override
    public void onSave(OrdineVendita ordine) {
        // La tua logica qui
        ordine.setCampo("YNOTE_INTERNE", "valore");
        super.onSave(ordine);
    }
}
```

## Override del calcolo prezzo

Per modificare il calcolo del prezzo finale:

```java
@Override
public BigDecimal calcolaPrezzo(OrdineVendita ordine) {
    BigDecimal prezzoBase = super.calcolaPrezzo(ordine);
    // Applica sconto custom
    return prezzoBase.multiply(new BigDecimal("0.9"));
}
```

## Validazioni custom

Le validazioni si aggiungono sovrascrivendo `valida()`:

```java
@Override
public List<String> confermaOrdine(OrdineVendita ordine) {
    List<String> errori = super.conferma(ordine);
    if (ordine.getImporto().compareTo(BigDecimal.ZERO) <= 0) {
        errori.add("L'importo deve essere maggiore di zero");
    }
    return errori;
}
```
