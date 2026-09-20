package com.ovelayos.animeav1;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class MegaClientTest {
    @Test public void prefersSubOverDubRegardlessOfPageOrder(){
        String dub="https://mega.nz/file/DUB#key",sub="https://mega.nz/file/SUB#key";
        String html="audio:'DUB',players:[{server:'Mega',url:'"+dub+"'}],audio:'SUB',players:[{server:'Mega',url:'"+sub+"'}]";
        assertEquals(List.of(sub,dub),MegaClient.orderedUrlsFromHtml(html));
    }

    @Test public void usesUnlabelledBeforeDub(){
        String dub="https://mega.nz/file/DUB#key",unknown="https://mega.nz/file/UNKNOWN#key";
        String html="players:[{server:'Mega',url:'"+unknown+"'}],audio:'DUB',players:[{server:'Mega',url:'"+dub+"'}]";
        assertEquals(List.of(unknown,dub),MegaClient.orderedUrlsFromHtml(html));
    }

    @Test public void keepsDubAsLastFallbackWhenItIsTheOnlyOption(){
        String dub="https://mega.nz/file/DUB#key";
        String html="type:'DUB',players:[{server:'Mega',url:'"+dub+"'}]";
        assertEquals(List.of(dub),MegaClient.orderedUrlsFromHtml(html));
    }
}
