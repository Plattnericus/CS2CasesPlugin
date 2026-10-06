package dev.plattnericus.cases.tools;

import java.util.ArrayList;
import java.util.List;

import static dev.plattnericus.cases.tools.WeaponSketch.Mat;
import static dev.plattnericus.cases.tools.WeaponSketch.circle;
import static dev.plattnericus.cases.tools.WeaponSketch.curve;
import static dev.plattnericus.cases.tools.WeaponSketch.ellipse;
import static dev.plattnericus.cases.tools.WeaponSketch.poly;
import static dev.plattnericus.cases.tools.WeaponSketch.rect;
import static dev.plattnericus.cases.tools.WeaponSketch.rot;
import static dev.plattnericus.cases.tools.WeaponSketch.rrect;

/**
 * Stylised side-view silhouettes for every weapon and knife type.
 * Coordinates are in a 256x256 design space; muzzles and blades point right.
 */
final class WeaponLibrary {

    private WeaponLibrary() {
    }

    static List<WeaponSketch> all() {
        List<WeaponSketch> list = new ArrayList<>();
        // ---------------------------------------------------------- pistols
        list.add(glock());
        list.add(usps());
        list.add(p2000());
        list.add(p250());
        list.add(fiveseven());
        list.add(tec9());
        list.add(cz75());
        list.add(dualies());
        list.add(deagle());
        list.add(revolver());
        // ---------------------------------------------------------- smgs
        list.add(mac10());
        list.add(mp9());
        list.add(mp7());
        list.add(mp5sd());
        list.add(ump45());
        list.add(p90());
        list.add(bizon());
        // ---------------------------------------------------------- rifles
        list.add(ak47());
        list.add(m4a4());
        list.add(m4a1s());
        list.add(galil());
        list.add(famas());
        list.add(aug());
        list.add(sg553());
        // ---------------------------------------------------------- snipers
        list.add(ssg08());
        list.add(awp());
        list.add(scar20());
        list.add(g3sg1());
        // ---------------------------------------------------------- heavy
        list.add(nova());
        list.add(xm1014());
        list.add(sawedoff());
        list.add(mag7());
        list.add(m249());
        list.add(negev());
        // ---------------------------------------------------------- equipment
        list.add(zeus());
        // ---------------------------------------------------------- knives
        list.add(karambit());
        list.add(butterfly());
        list.add(m9());
        list.add(bayonet());
        list.add(flip());
        list.add(gut());
        list.add(huntsman());
        list.add(falchion());
        list.add(bowie());
        list.add(shadowDaggers());
        list.add(navaja());
        list.add(stiletto());
        list.add(talon());
        list.add(ursus());
        list.add(classic());
        list.add(paracord());
        list.add(survival());
        list.add(nomad());
        list.add(skeleton());
        list.add(kukri());
        return list;
    }

    // ================================================================= pistol parts

    private static void pistolGrip(WeaponSketch s, double x, double y, double w, double h, double slant) {
        s.paint(poly(x, y, x + w, y, x + w - slant, y + h, x - slant - 4, y + h - 2));
        s.dark(poly(x + 3, y + 10, x + w - 4, y + 10, x + w - slant - 3, y + h - 8, x - slant, y + h - 10));
    }

    private static void triggerGuard(WeaponSketch s, double x, double y, double w, double h) {
        s.paint(rrect(x, y, w, h, h));
        s.cut(rrect(x + 4, y + 3, w - 9, h - 7, h - 6));
    }

    private static WeaponSketch glock() {
        WeaponSketch s = new WeaponSketch("glock18");
        triggerGuard(s, 98, 120, 42, 24);
        pistolGrip(s, 62, 118, 40, 70, 14);
        s.paint(rrect(40, 92, 168, 30, 6));
        s.paint(poly(44, 120, 200, 120, 196, 132, 118, 132, 104, 126, 60, 126));
        for (int i = 0; i < 6; i++) {
            s.dark(rect(52 + i * 6, 96, 2.5, 22));
        }
        s.metal(rect(206, 98, 8, 14));
        s.dark(rect(42, 88, 8, 5));
        s.dark(rect(194, 88, 6, 5));
        return s;
    }

    private static WeaponSketch usps() {
        WeaponSketch s = new WeaponSketch("usps");
        triggerGuard(s, 82, 120, 40, 24);
        pistolGrip(s, 44, 118, 40, 68, 14);
        s.paint(rrect(22, 94, 140, 28, 6));
        s.paint(poly(26, 120, 150, 120, 146, 132, 100, 132, 88, 126, 40, 126));
        s.paint(rrect(160, 96, 88, 22, 10));
        s.dark(rect(160, 96, 6, 22));
        for (int i = 0; i < 5; i++) {
            s.dark(rect(30 + i * 6, 98, 2.5, 20));
        }
        s.dark(rect(24, 90, 8, 5));
        return s;
    }

    private static WeaponSketch p2000() {
        WeaponSketch s = new WeaponSketch("p2000");
        triggerGuard(s, 100, 120, 42, 24);
        pistolGrip(s, 62, 118, 42, 70, 12);
        s.paint(rrect(42, 92, 166, 30, 8));
        s.paint(poly(46, 120, 196, 120, 192, 134, 118, 134, 104, 126, 60, 126));
        for (int i = 0; i < 6; i++) {
            s.dark(rect(54 + i * 6, 96, 2.5, 22));
        }
        s.metal(rect(206, 100, 8, 12));
        return s;
    }

    private static WeaponSketch p250() {
        WeaponSketch s = new WeaponSketch("p250");
        triggerGuard(s, 102, 122, 40, 24);
        pistolGrip(s, 66, 120, 42, 66, 10);
        s.paint(rrect(48, 94, 156, 30, 5));
        s.paint(poly(52, 122, 196, 122, 190, 136, 120, 136, 108, 128, 62, 128));
        for (int i = 0; i < 7; i++) {
            s.dark(rect(58 + i * 5, 98, 2, 22));
        }
        s.metal(rect(202, 100, 8, 12));
        return s;
    }

    private static WeaponSketch fiveseven() {
        WeaponSketch s = new WeaponSketch("fiveseven");
        triggerGuard(s, 104, 118, 42, 22);
        pistolGrip(s, 60, 116, 46, 74, 18);
        s.paint(rrect(36, 96, 182, 24, 4));
        s.paint(poly(40, 118, 208, 118, 200, 132, 122, 132, 108, 124, 54, 124));
        for (int i = 0; i < 8; i++) {
            s.dark(rect(44 + i * 5, 99, 2, 18));
        }
        s.metal(rect(216, 100, 10, 12));
        return s;
    }

    private static WeaponSketch tec9() {
        WeaponSketch s = new WeaponSketch("tec9");
        s.paint(rrect(30, 100, 150, 30, 4));
        s.metal(rect(178, 108, 60, 10));
        s.paint(rrect(178, 104, 20, 18, 4));
        s.paint(poly(116, 128, 136, 128, 140, 196, 120, 196));
        s.dark(rect(122, 140, 12, 50));
        triggerGuard(s, 78, 126, 38, 22);
        pistolGrip(s, 44, 126, 36, 56, 12);
        s.dark(rect(36, 104, 30, 6));
        return s;
    }

    private static WeaponSketch cz75() {
        WeaponSketch s = new WeaponSketch("cz75");
        triggerGuard(s, 100, 120, 40, 24);
        pistolGrip(s, 62, 118, 42, 70, 14);
        s.paint(rrect(40, 94, 168, 28, 6));
        s.paint(poly(44, 120, 196, 120, 192, 132, 116, 132, 104, 126, 58, 126));
        s.paint(rrect(166, 128, 18, 44, 4));
        s.dark(rect(170, 136, 10, 30));
        for (int i = 0; i < 6; i++) {
            s.dark(rect(52 + i * 6, 98, 2.5, 20));
        }
        s.metal(rect(206, 100, 8, 12));
        return s;
    }

    private static WeaponSketch dualies() {
        WeaponSketch s = new WeaponSketch("dual_berettas");
        // rear pistol, offset up-left
        s.paint(rrect(28, 74, 150, 24, 6));
        s.paint(poly(48, 96, 82, 96, 70, 150, 40, 148));
        s.metal(rect(176, 80, 14, 9));
        // front pistol
        triggerGuard(s, 106, 144, 38, 22);
        pistolGrip(s, 70, 142, 38, 64, 12);
        s.paint(rrect(50, 118, 160, 26, 6));
        s.cut(rrect(120, 122, 60, 8, 4));
        s.paint(poly(54, 142, 196, 142, 192, 154, 124, 154, 110, 148, 64, 148));
        s.metal(rect(208, 124, 12, 10));
        return s;
    }

    private static WeaponSketch deagle() {
        WeaponSketch s = new WeaponSketch("deagle");
        triggerGuard(s, 104, 126, 44, 26);
        pistolGrip(s, 62, 124, 46, 74, 12);
        s.paint(rrect(30, 86, 196, 40, 6));
        s.light(poly(110, 88, 222, 88, 222, 96, 110, 96));
        s.paint(poly(36, 124, 214, 124, 206, 138, 126, 138, 112, 130, 52, 130));
        for (int i = 0; i < 6; i++) {
            s.dark(rect(40 + i * 7, 92, 3, 28));
        }
        s.metal(rect(220, 96, 10, 18));
        s.dark(rect(30, 80, 10, 6));
        return s;
    }

    private static WeaponSketch revolver() {
        WeaponSketch s = new WeaponSketch("r8_revolver");
        s.paint(curve(52, 128, 92, 130, 84, 196, 56, 198, 44, 170));
        s.wood(curve(58, 140, 84, 140, 78, 186, 60, 188, 52, 166));
        triggerGuard(s, 88, 126, 34, 26);
        s.paint(rrect(70, 96, 64, 34, 10));
        s.dark(rect(84, 100, 3, 26));
        s.dark(rect(100, 100, 3, 26));
        s.dark(rect(116, 100, 3, 26));
        s.paint(rrect(130, 98, 112, 18, 4));
        s.paint(rect(130, 114, 80, 8));
        s.metal(rect(236, 92, 6, 8));
        s.metal(poly(60, 96, 74, 88, 78, 100));
        return s;
    }

    private static WeaponSketch zeus() {
        WeaponSketch s = new WeaponSketch("zeus");
        s.paint(rrect(56, 96, 150, 40, 12));
        s.dark(rrect(196, 102, 30, 28, 6));
        s.light(rect(224, 106, 8, 6));
        s.light(rect(224, 120, 8, 6));
        triggerGuard(s, 112, 132, 40, 24);
        pistolGrip(s, 76, 132, 42, 64, 10);
        s.dark(rect(70, 104, 50, 6));
        return s;
    }

    // ================================================================= smgs

    private static WeaponSketch mac10() {
        WeaponSketch s = new WeaponSketch("mac10");
        s.paint(rrect(46, 98, 150, 38, 4));
        s.metal(rect(194, 108, 44, 10));
        s.paint(poly(98, 134, 128, 134, 124, 210, 94, 210));
        s.dark(rect(102, 150, 18, 54));
        triggerGuard(s, 126, 132, 34, 22);
        s.metal(rect(20, 108, 30, 6));
        s.metal(rect(20, 108, 6, 22));
        s.dark(rect(60, 102, 40, 6));
        return s;
    }

    private static WeaponSketch mp9() {
        WeaponSketch s = new WeaponSketch("mp9");
        s.dark(poly(10, 112, 54, 112, 54, 120, 18, 120, 18, 132, 10, 132));
        s.paint(rrect(50, 100, 150, 30, 6));
        s.metal(rect(198, 110, 40, 9));
        s.paint(poly(102, 128, 126, 128, 122, 200, 98, 200));
        s.dark(rect(104, 140, 14, 56));
        triggerGuard(s, 124, 128, 34, 22);
        s.paint(poly(170, 128, 186, 128, 182, 168, 168, 168));
        s.dark(rect(64, 94, 110, 7));
        return s;
    }

    private static WeaponSketch mp7() {
        WeaponSketch s = new WeaponSketch("mp7");
        s.metal(rect(14, 112, 50, 6));
        s.dark(rrect(10, 104, 12, 30, 4));
        s.paint(rrect(56, 98, 136, 34, 8));
        s.metal(rect(190, 110, 48, 8));
        s.paint(poly(104, 130, 128, 130, 124, 204, 100, 204));
        s.dark(rect(106, 142, 14, 56));
        triggerGuard(s, 128, 130, 30, 20);
        s.paint(poly(164, 130, 178, 130, 176, 166, 162, 166));
        s.dark(rect(70, 90, 110, 9));
        return s;
    }

    private static WeaponSketch mp5sd() {
        WeaponSketch s = new WeaponSketch("mp5sd");
        s.paint(poly(10, 110, 52, 112, 52, 132, 14, 144));
        s.paint(rrect(50, 104, 92, 26, 5));
        s.paint(rrect(140, 104, 104, 24, 10));
        s.dark(rect(140, 104, 5, 24));
        s.paint(curve(110, 128, 126, 128, 140, 170, 128, 176, 114, 152));
        triggerGuard(s, 80, 128, 30, 20);
        pistolGrip(s, 60, 128, 24, 46, 8);
        s.metal(rect(64, 96, 50, 9));
        return s;
    }

    private static WeaponSketch ump45() {
        WeaponSketch s = new WeaponSketch("ump45");
        s.paint(poly(10, 104, 60, 104, 60, 112, 20, 112, 20, 132, 60, 132, 60, 138, 10, 138));
        s.paint(rrect(56, 98, 150, 34, 4));
        s.metal(rect(204, 108, 36, 10));
        s.paint(poly(130, 130, 150, 130, 156, 196, 136, 196));
        s.dark(rect(138, 142, 12, 48));
        triggerGuard(s, 98, 130, 30, 20);
        pistolGrip(s, 72, 130, 28, 50, 8);
        s.dark(rect(70, 90, 120, 8));
        return s;
    }

    private static WeaponSketch p90() {
        WeaponSketch s = new WeaponSketch("p90");
        s.paint(curve(14, 120, 30, 96, 196, 96, 226, 112, 222, 140, 150, 152, 110, 158, 40, 150));
        s.cut(ellipse(118, 138, 18, 9));
        s.light(rrect(46, 86, 150, 12, 4));
        s.dark(rect(52, 88, 138, 3));
        s.metal(rect(222, 112, 20, 10));
        s.dark(rect(40, 112, 40, 30));
        return s;
    }

    private static WeaponSketch bizon() {
        WeaponSketch s = new WeaponSketch("ppbizon");
        s.paint(poly(10, 106, 64, 108, 64, 128, 16, 140));
        s.paint(rrect(60, 100, 120, 28, 5));
        s.metal(rect(178, 110, 62, 9));
        s.paint(rrect(100, 126, 110, 26, 13));
        s.dark(rect(110, 132, 92, 3));
        s.dark(rect(110, 142, 92, 3));
        triggerGuard(s, 76, 126, 28, 22);
        pistolGrip(s, 62, 126, 22, 50, 8);
        s.metal(rect(70, 92, 90, 9));
        return s;
    }

    // ================================================================= rifles

    private static WeaponSketch ak47() {
        WeaponSketch s = new WeaponSketch("ak47");
        s.paint(poly(12, 114, 66, 116, 66, 134, 18, 150, 10, 144));
        s.paint(rrect(64, 108, 86, 22, 4));
        s.paint(poly(64, 110, 150, 110, 146, 103, 72, 103));
        pistolGrip(s, 90, 128, 16, 32, 6);
        triggerGuard(s, 102, 126, 26, 16);
        s.paint(curve(124, 128, 142, 128, 156, 168, 138, 176, 128, 150));
        s.paint(rrect(148, 110, 44, 17, 6));
        s.paint(rect(148, 104, 52, 6));
        s.metal(rect(190, 114, 46, 6));
        s.metal(poly(222, 114, 226, 102, 232, 102, 234, 114));
        s.metal(rect(232, 111, 12, 11));
        s.metal(rect(146, 99, 10, 5));
        return s;
    }

    private static WeaponSketch m4a4() {
        WeaponSketch s = new WeaponSketch("m4a4");
        s.paint(poly(10, 112, 56, 112, 56, 136, 14, 142));
        s.metal(rect(52, 116, 18, 10));
        s.paint(rrect(66, 114, 74, 20, 3));
        s.paint(rect(66, 104, 92, 12));
        s.dark(rect(70, 98, 84, 7));
        pistolGrip(s, 84, 132, 16, 32, 7);
        triggerGuard(s, 96, 130, 26, 16);
        s.paint(poly(118, 132, 138, 132, 142, 172, 124, 174));
        s.paint(rrect(154, 104, 46, 22, 4));
        s.dark(rect(158, 108, 38, 3));
        s.metal(rect(198, 112, 34, 6));
        s.metal(rect(230, 109, 14, 11));
        s.metal(poly(196, 104, 200, 92, 206, 92, 208, 104));
        return s;
    }

    private static WeaponSketch m4a1s() {
        WeaponSketch s = new WeaponSketch("m4a1s");
        s.paint(poly(10, 114, 52, 114, 52, 134, 14, 138));
        s.metal(rect(48, 117, 20, 9));
        s.paint(rrect(64, 114, 70, 20, 3));
        s.paint(rect(64, 104, 84, 12));
        s.dark(rect(68, 98, 76, 7));
        pistolGrip(s, 80, 132, 16, 32, 7);
        triggerGuard(s, 92, 130, 26, 16);
        s.paint(poly(112, 132, 130, 132, 134, 170, 118, 172));
        s.paint(rrect(146, 104, 40, 22, 4));
        s.metal(rect(184, 112, 12, 6));
        s.paint(rrect(194, 107, 52, 16, 6));
        s.dark(rect(194, 107, 4, 16));
        return s;
    }

    private static WeaponSketch galil() {
        WeaponSketch s = new WeaponSketch("galil");
        s.paint(poly(10, 110, 64, 114, 64, 134, 16, 146, 8, 138));
        s.cut(poly(22, 120, 56, 121, 56, 130, 24, 137));
        s.paint(rrect(62, 108, 82, 22, 4));
        s.dark(rect(66, 102, 72, 7));
        pistolGrip(s, 86, 128, 16, 32, 6);
        triggerGuard(s, 98, 126, 26, 16);
        s.paint(curve(118, 128, 136, 128, 148, 164, 132, 172, 122, 150));
        s.paint(rrect(142, 108, 54, 18, 7));
        s.metal(rect(194, 113, 40, 6));
        s.metal(rect(232, 110, 12, 11));
        s.metal(poly(214, 113, 218, 102, 224, 102, 226, 113));
        return s;
    }

    private static WeaponSketch famas() {
        WeaponSketch s = new WeaponSketch("famas");
        s.paint(poly(16, 116, 200, 114, 208, 124, 200, 136, 124, 136, 114, 132, 30, 142, 14, 132));
        s.paint(poly(70, 116, 78, 94, 188, 94, 194, 114));
        s.cut(poly(86, 101, 178, 101, 180, 111, 84, 111));
        s.paint(poly(58, 136, 76, 136, 78, 166, 60, 166));
        s.dark(rect(62, 142, 12, 20));
        pistolGrip(s, 124, 134, 16, 32, 6);
        triggerGuard(s, 134, 132, 24, 16);
        s.metal(rect(204, 119, 36, 6));
        s.metal(rect(236, 116, 8, 11));
        return s;
    }

    private static WeaponSketch aug() {
        WeaponSketch s = new WeaponSketch("aug");
        s.paint(curve(14, 112, 120, 110, 194, 114, 200, 134, 130, 138, 26, 146, 12, 130));
        s.dark(rrect(84, 92, 74, 16, 7));
        s.light(rect(152, 95, 6, 10));
        s.paint(rect(100, 106, 40, 6));
        s.paint(poly(70, 138, 88, 138, 88, 166, 72, 166));
        s.paint(poly(150, 134, 166, 134, 164, 172, 148, 172));
        triggerGuard(s, 120, 134, 30, 18);
        s.metal(rect(196, 118, 40, 6));
        s.metal(rect(234, 115, 10, 11));
        return s;
    }

    private static WeaponSketch sg553() {
        WeaponSketch s = new WeaponSketch("sg553");
        s.paint(poly(10, 112, 62, 112, 62, 136, 16, 144));
        s.cut(poly(22, 120, 54, 119, 54, 130, 24, 135));
        s.paint(rrect(60, 108, 86, 24, 4));
        s.dark(rrect(78, 90, 72, 14, 6));
        s.light(rect(146, 92, 5, 10));
        s.metal(rect(96, 102, 30, 6));
        pistolGrip(s, 82, 130, 16, 32, 6);
        triggerGuard(s, 94, 128, 26, 16);
        s.paint(curve(118, 130, 136, 130, 146, 166, 130, 172, 122, 150));
        s.paint(rrect(144, 108, 52, 18, 6));
        s.metal(rect(194, 113, 38, 6));
        s.metal(rect(230, 110, 14, 11));
        return s;
    }

    // ================================================================= snipers

    private static WeaponSketch ssg08() {
        WeaponSketch s = new WeaponSketch("ssg08");
        s.paint(poly(10, 114, 84, 118, 98, 124, 94, 138, 62, 136, 22, 152, 10, 146));
        s.paint(rrect(92, 114, 54, 14, 3));
        s.metal(rect(144, 116, 100, 4));
        s.paint(rrect(80, 98, 78, 12, 6));
        s.paint(rrect(76, 96, 12, 16, 4));
        s.paint(rrect(150, 95, 14, 18, 4));
        s.metal(rect(104, 108, 4, 8));
        s.metal(rect(132, 108, 4, 8));
        s.metal(poly(96, 126, 104, 126, 112, 136, 106, 138));
        triggerGuard(s, 104, 126, 22, 14);
        s.dark(rect(116, 126, 14, 12));
        return s;
    }

    private static WeaponSketch awp() {
        WeaponSketch s = new WeaponSketch("awp");
        s.paint(poly(8, 110, 98, 110, 102, 124, 98, 144, 72, 142, 62, 156, 20, 158, 8, 148));
        s.cut(ellipse(72, 128, 10, 7));
        s.paint(rrect(96, 110, 62, 18, 3));
        s.metal(rect(156, 114, 82, 7));
        s.metal(rect(234, 110, 12, 14));
        s.paint(rrect(86, 92, 84, 13, 6));
        s.dark(rrect(78, 89, 16, 19, 5));
        s.dark(rrect(160, 88, 18, 21, 5));
        s.metal(rect(104, 104, 5, 7));
        s.metal(rect(146, 104, 5, 7));
        triggerGuard(s, 98, 126, 26, 16);
        s.dark(rect(120, 126, 18, 16));
        return s;
    }

    private static WeaponSketch scar20() {
        WeaponSketch s = new WeaponSketch("scar20");
        s.paint(poly(8, 108, 60, 110, 60, 136, 12, 150, 8, 140));
        s.paint(rrect(58, 108, 98, 24, 4));
        s.dark(rect(62, 101, 100, 8));
        s.paint(rrect(70, 86, 80, 12, 6));
        s.dark(rrect(64, 84, 12, 16, 4));
        s.dark(rrect(146, 83, 14, 18, 4));
        pistolGrip(s, 84, 130, 16, 34, 6);
        triggerGuard(s, 96, 128, 26, 16);
        s.paint(poly(120, 130, 140, 130, 142, 160, 122, 160));
        s.paint(rrect(154, 108, 46, 20, 4));
        s.metal(rect(198, 114, 38, 6));
        s.metal(rect(234, 111, 12, 12));
        return s;
    }

    private static WeaponSketch g3sg1() {
        WeaponSketch s = new WeaponSketch("g3sg1");
        s.paint(poly(8, 110, 64, 112, 64, 132, 30, 136, 12, 152, 8, 144));
        s.cut(poly(20, 120, 56, 120, 56, 127, 22, 131));
        s.paint(rrect(62, 108, 92, 22, 4));
        s.paint(rrect(70, 88, 80, 12, 6));
        s.dark(rrect(64, 86, 12, 16, 4));
        s.dark(rrect(146, 85, 14, 18, 4));
        s.metal(rect(94, 100, 6, 8));
        s.metal(rect(126, 100, 6, 8));
        pistolGrip(s, 84, 128, 16, 34, 6);
        triggerGuard(s, 96, 126, 26, 16);
        s.paint(poly(122, 128, 140, 128, 140, 160, 122, 160));
        s.paint(rrect(152, 108, 48, 18, 8));
        s.metal(rect(198, 113, 38, 6));
        s.metal(rect(234, 110, 12, 12));
        return s;
    }

    // ================================================================= heavy

    private static WeaponSketch nova() {
        WeaponSketch s = new WeaponSketch("nova");
        s.paint(poly(8, 112, 70, 116, 70, 134, 14, 150, 8, 142));
        s.paint(rrect(68, 110, 64, 22, 4));
        triggerGuard(s, 84, 128, 26, 16);
        s.metal(rect(130, 110, 112, 7));
        s.metal(rect(130, 119, 100, 6));
        s.paint(rrect(150, 116, 52, 14, 6));
        s.dark(rect(156, 118, 40, 2));
        s.metal(rect(238, 106, 4, 6));
        return s;
    }

    private static WeaponSketch xm1014() {
        WeaponSketch s = new WeaponSketch("xm1014");
        s.paint(poly(8, 110, 70, 114, 70, 134, 14, 150, 8, 142));
        s.cut(poly(20, 120, 60, 121, 60, 130, 24, 140));
        s.paint(rrect(68, 108, 76, 24, 4));
        pistolGrip(s, 76, 130, 16, 32, 6);
        triggerGuard(s, 90, 128, 26, 16);
        s.metal(rect(142, 110, 100, 7));
        s.metal(rect(142, 119, 88, 7));
        s.paint(rrect(150, 116, 44, 16, 6));
        s.dark(rect(72, 102, 64, 7));
        return s;
    }

    private static WeaponSketch sawedoff() {
        WeaponSketch s = new WeaponSketch("sawedoff");
        s.paint(curve(30, 128, 70, 116, 92, 120, 88, 140, 60, 190, 36, 186, 42, 150));
        s.paint(rrect(84, 112, 54, 22, 5));
        triggerGuard(s, 94, 130, 26, 16);
        s.metal(rect(136, 112, 82, 8));
        s.metal(rect(136, 121, 82, 8));
        s.paint(rrect(146, 118, 48, 16, 6));
        s.dark(rect(214, 110, 6, 21));
        return s;
    }

    private static WeaponSketch mag7() {
        WeaponSketch s = new WeaponSketch("mag7");
        s.paint(poly(20, 112, 52, 114, 52, 132, 26, 142));
        s.paint(rrect(50, 104, 122, 32, 6));
        s.paint(poly(86, 134, 112, 134, 110, 188, 86, 188));
        s.dark(rect(90, 146, 16, 36));
        s.paint(poly(140, 134, 158, 134, 156, 172, 140, 172));
        s.metal(rect(170, 110, 70, 12));
        s.paint(rrect(176, 118, 50, 14, 5));
        s.dark(rect(60, 98, 80, 7));
        return s;
    }

    private static WeaponSketch m249() {
        WeaponSketch s = new WeaponSketch("m249");
        s.paint(poly(8, 112, 54, 112, 54, 134, 14, 144));
        s.paint(rrect(52, 106, 98, 28, 4));
        s.dark(rect(60, 98, 84, 9));
        s.paint(rrect(94, 132, 46, 36, 4));
        s.dark(rect(98, 138, 38, 3));
        pistolGrip(s, 66, 132, 16, 32, 6);
        triggerGuard(s, 78, 130, 22, 16);
        s.paint(rrect(148, 108, 44, 22, 4));
        s.metal(rect(190, 114, 46, 7));
        s.metal(rect(234, 111, 10, 13));
        s.metal(poly(196, 120, 200, 120, 214, 168, 210, 170));
        s.metal(poly(200, 120, 204, 120, 190, 170, 186, 168));
        s.metal(rect(120, 92, 20, 8));
        return s;
    }

    private static WeaponSketch negev() {
        WeaponSketch s = new WeaponSketch("negev");
        s.paint(poly(8, 110, 50, 114, 50, 132, 12, 146));
        s.cut(poly(18, 120, 44, 120, 44, 128, 20, 136));
        s.paint(rrect(48, 104, 104, 28, 4));
        s.dark(rect(54, 96, 94, 9));
        s.paint(rrect(98, 130, 34, 30, 6));
        pistolGrip(s, 66, 130, 16, 32, 6);
        triggerGuard(s, 78, 128, 22, 16);
        s.paint(rrect(150, 106, 40, 24, 4));
        s.metal(rect(188, 113, 48, 7));
        s.metal(rect(234, 110, 10, 13));
        s.paint(poly(160, 128, 172, 128, 170, 160, 158, 160));
        return s;
    }

    // ================================================================= knives

    private static WeaponSketch karambit() {
        WeaponSketch s = new WeaponSketch("karambit");
        s.ring(Mat.PAINT, 38, 132, 19, 10);
        s.dark(curve(52, 120, 92, 116, 126, 120, 128, 140, 92, 146, 54, 142));
        s.metal(rect(62, 126, 4, 4));
        s.metal(rect(98, 126, 4, 4));
        s.paint(curve(120, 118, 168, 108, 210, 114, 236, 138, 240, 172, 224, 150, 196, 138, 156, 140, 124, 142));
        return s;
    }

    private static WeaponSketch butterfly() {
        WeaponSketch s = new WeaponSketch("butterfly");
        s.paint(rrect(18, 112, 112, 13, 6));
        s.paint(rrect(18, 127, 112, 13, 6));
        s.cut(rrect(32, 116, 84, 5, 3));
        s.cut(rrect(32, 131, 84, 5, 3));
        s.metal(rect(126, 114, 8, 24));
        s.metal(rrect(10, 116, 14, 20, 4));
        s.paint(poly(132, 116, 206, 116, 242, 124, 210, 138, 132, 138));
        s.metal(circle(138, 127, 3));
        return s;
    }

    private static WeaponSketch m9() {
        WeaponSketch s = new WeaponSketch("m9_bayonet");
        s.dark(rrect(22, 114, 88, 26, 10));
        for (int i = 0; i < 6; i++) {
            s.add(Mat.METAL, rect(34 + i * 12, 117, 3, 20));
        }
        s.ring(Mat.METAL, 18, 126, 9, 5);
        s.metal(rrect(106, 104, 10, 46, 4));
        s.paint(poly(114, 112, 212, 112, 244, 126, 214, 142, 114, 142));
        for (int i = 0; i < 7; i++) {
            double x = 132 + i * 9;
            s.cut(poly(x, 112, x + 4.5, 117, x + 9, 112));
        }
        s.cut(ellipse(130, 132, 7, 3.5));
        return s;
    }

    private static WeaponSketch bayonet() {
        WeaponSketch s = new WeaponSketch("bayonet");
        s.dark(rrect(24, 116, 84, 22, 8));
        for (int i = 0; i < 5; i++) {
            s.add(Mat.METAL, rect(36 + i * 14, 119, 3, 16));
        }
        s.metal(rrect(16, 114, 12, 26, 4));
        s.metal(rrect(104, 108, 9, 38, 4));
        s.ring(Mat.METAL, 108, 104, 7, 4);
        s.paint(poly(112, 117, 220, 117, 246, 127, 220, 137, 112, 137));
        s.light(rect(124, 125, 80, 2));
        return s;
    }

    private static WeaponSketch flip() {
        WeaponSketch s = new WeaponSketch("flip");
        s.dark(curve(26, 120, 70, 114, 126, 116, 128, 138, 70, 142, 28, 136));
        s.metal(circle(42, 128, 3));
        s.metal(circle(112, 127, 4));
        s.paint(poly(122, 116, 190, 110, 240, 122, 216, 136, 122, 140));
        s.cut(circle(140, 122, 4.5));
        return s;
    }

    private static WeaponSketch gut() {
        WeaponSketch s = new WeaponSketch("gut");
        s.dark(rrect(24, 116, 92, 24, 10));
        s.metal(rect(110, 112, 8, 32));
        s.paint(poly(116, 118, 156, 118, 164, 108, 180, 110, 176, 116, 168, 118, 230, 122, 244, 132, 214, 142, 116, 142));
        for (int i = 0; i < 4; i++) {
            double x = 190 + i * 8;
            s.cut(poly(x, 120, x + 4, 124, x + 8, 120.6));
        }
        return s;
    }

    private static WeaponSketch huntsman() {
        WeaponSketch s = new WeaponSketch("huntsman");
        s.dark(curve(16, 118, 60, 114, 104, 116, 106, 140, 60, 144, 18, 140));
        s.metal(rrect(100, 106, 10, 42, 4));
        s.paint(poly(108, 112, 196, 112, 212, 104, 246, 120, 224, 140, 108, 146));
        for (int i = 0; i < 6; i++) {
            double x = 120 + i * 11;
            s.cut(poly(x, 112, x + 5.5, 118, x + 11, 112));
        }
        return s;
    }

    private static WeaponSketch falchion() {
        WeaponSketch s = new WeaponSketch("falchion");
        s.dark(curve(20, 128, 50, 116, 108, 118, 112, 138, 54, 144, 22, 140));
        s.metal(rect(108, 114, 8, 26));
        s.paint(poly(114, 118, 176, 116, 222, 104, 242, 98, 232, 120, 196, 138, 114, 140));
        return s;
    }

    private static WeaponSketch bowie() {
        WeaponSketch s = new WeaponSketch("bowie");
        s.wood(curve(14, 120, 56, 114, 100, 116, 102, 142, 56, 146, 16, 142));
        s.dark(rect(36, 116, 4, 28));
        s.dark(rect(64, 115, 4, 30));
        s.metal(rrect(98, 100, 12, 56, 5));
        s.paint(poly(108, 110, 188, 110, 210, 102, 248, 118, 222, 144, 108, 148));
        s.light(rect(118, 116, 70, 2));
        return s;
    }

    private static WeaponSketch shadowDaggers() {
        WeaponSketch s = new WeaponSketch("shadow_daggers");
        s.dark(rrect(32, 84, 26, 88, 12));
        s.metal(rect(56, 116, 18, 22));
        s.paint(poly(72, 112, 176, 116, 232, 127, 176, 138, 72, 142));
        s.light(rect(84, 126, 80, 2));
        return s;
    }

    private static WeaponSketch navaja() {
        WeaponSketch s = new WeaponSketch("navaja");
        s.dark(curve(20, 134, 50, 124, 110, 120, 114, 136, 60, 144, 24, 146));
        s.metal(circle(104, 129, 3));
        s.paint(poly(110, 122, 168, 118, 218, 108, 244, 106, 212, 126, 110, 136));
        return s;
    }

    private static WeaponSketch stiletto() {
        WeaponSketch s = new WeaponSketch("stiletto");
        s.dark(rrect(24, 119, 98, 18, 8));
        s.metal(rrect(18, 118, 12, 20, 4));
        s.metal(rrect(114, 117, 10, 22, 3));
        s.paint(poly(122, 120, 226, 121, 248, 128, 226, 135, 122, 136));
        s.light(rect(130, 127, 88, 2));
        return s;
    }

    private static WeaponSketch talon() {
        WeaponSketch s = new WeaponSketch("talon");
        s.ring(Mat.METAL, 26, 134, 16, 8);
        s.dark(curve(36, 122, 84, 116, 134, 120, 136, 142, 86, 146, 40, 146));
        s.paint(curve(128, 118, 180, 110, 220, 120, 242, 146, 238, 172, 220, 148, 190, 138, 160, 142, 130, 142));
        return s;
    }

    private static WeaponSketch ursus() {
        WeaponSketch s = new WeaponSketch("ursus");
        s.dark(curve(20, 118, 64, 112, 116, 116, 118, 142, 64, 146, 22, 142));
        s.metal(circle(40, 130, 3));
        s.metal(circle(104, 129, 4));
        s.paint(poly(116, 114, 206, 114, 246, 124, 224, 140, 116, 142));
        s.cut(circle(130, 122, 4));
        return s;
    }

    private static WeaponSketch classic() {
        WeaponSketch s = new WeaponSketch("classic");
        s.dark(rrect(22, 118, 88, 20, 8));
        for (int i = 0; i < 9; i++) {
            s.add(Mat.LIGHT, rect(30 + i * 9, 119, 2, 18));
        }
        s.metal(rrect(16, 116, 10, 24, 4));
        s.metal(rrect(106, 106, 10, 44, 4));
        s.paint(poly(114, 116, 206, 116, 246, 128, 214, 140, 130, 140, 124, 134, 114, 134));
        return s;
    }

    private static WeaponSketch paracord() {
        WeaponSketch s = new WeaponSketch("paracord");
        s.ring(Mat.METAL, 22, 128, 10, 5);
        s.dark(rrect(30, 118, 82, 20, 8));
        for (int i = 0; i < 10; i++) {
            s.add(i % 2 == 0 ? Mat.LIGHT : Mat.DARK, poly(36 + i * 7.5, 118, 41 + i * 7.5, 118, 37 + i * 7.5, 138, 32 + i * 7.5, 138));
        }
        s.metal(rect(110, 116, 8, 24));
        s.paint(poly(116, 116, 196, 116, 244, 126, 212, 140, 116, 140));
        return s;
    }

    private static WeaponSketch survival() {
        WeaponSketch s = new WeaponSketch("survival");
        s.dark(rrect(22, 116, 90, 24, 10));
        for (int i = 0; i < 4; i++) {
            s.add(Mat.METAL, rect(36 + i * 18, 120, 6, 16));
        }
        s.metal(rect(108, 110, 8, 36));
        s.paint(poly(114, 114, 210, 114, 246, 126, 214, 142, 114, 142));
        for (int i = 0; i < 6; i++) {
            double x = 124 + i * 9;
            s.cut(poly(x, 114, x + 4.5, 119, x + 9, 114));
        }
        return s;
    }

    private static WeaponSketch nomad() {
        WeaponSketch s = new WeaponSketch("nomad");
        s.ring(Mat.METAL, 20, 130, 9, 5);
        s.dark(curve(26, 120, 70, 114, 116, 118, 118, 140, 70, 146, 28, 140));
        s.paint(poly(114, 116, 196, 112, 246, 124, 216, 140, 114, 142));
        return s;
    }

    private static WeaponSketch skeleton() {
        WeaponSketch s = new WeaponSketch("skeleton");
        s.ring(Mat.PAINT, 22, 130, 11, 6);
        s.paint(rrect(30, 118, 86, 22, 8));
        s.cut(rrect(40, 123, 22, 12, 6));
        s.cut(rrect(68, 123, 22, 12, 6));
        s.cut(circle(102, 129, 5));
        s.paint(poly(114, 116, 196, 114, 246, 126, 214, 140, 114, 142));
        return s;
    }

    private static WeaponSketch kukri() {
        WeaponSketch s = new WeaponSketch("kukri");
        s.wood(curve(14, 114, 30, 120, 90, 120, 94, 140, 30, 140, 12, 146));
        s.metal(rect(90, 116, 10, 28));
        s.paint(curve(98, 116, 150, 116, 200, 122, 238, 140, 246, 166, 226, 168, 196, 152, 150, 144, 98, 142));
        return s;
    }
}
