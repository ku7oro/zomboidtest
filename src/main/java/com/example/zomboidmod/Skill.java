package com.example.zomboidmod;

public enum Skill {
    FITNESS("Выносливость"),
    STRENGTH("Сила"),
    STEALTH("Скрытность"),
    FIRST_AID("Медицина");

    public final String title;

    Skill(String title) {
        this.title = title;
    }
}
