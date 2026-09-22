package com.dart998.animeav1;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class AnimeAv1ClientTest {
    @Test public void parsesRealLibraryFieldsAndEscapedTitles() {
        String html = "<script>const page={libraryEntries:[" +
                "{status:0,episode:4,mediaId:12,media:{title:\"Serie \\\"A\\\"\",slug:\"serie-a\",episodesCount:12}}," +
                "{status:1,episode:8,media:{title:\"Terminada\",slug:\"terminada\"}}" +
                "]};</script>";

        List<AnimeAv1Client.LibraryItem> items = AnimeAv1Client.parseLibrary(html);

        assertEquals(2, items.size());
        assertEquals("serie-a", items.get(0).slug);
        assertEquals("Serie \"A\"", items.get(0).title);
        assertEquals(0, items.get(0).status);
        assertEquals(4, items.get(0).watched);
        assertEquals(1, items.get(1).status);
        assertEquals(8, items.get(1).watched);
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsPagesWithoutLibraryPayload() {
        AnimeAv1Client.parseLibrary("<html>Iniciar sesión</html>");
    }
}
