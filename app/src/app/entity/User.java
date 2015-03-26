package app.entity;

import toonly.dbmanager.base.*;
import toonly.dbmanager.lowlevel.DT;
import toonly.dbmanager.repos.Updatable;
import toonly.mapper.ParamConstructable;

/**
 * Created by caoyouxin on 15-3-3.
 */
public class User implements Updatable, Addable, Delable, Modable, Selable, Jsonable, ParamConstructable {

    @Column
    @KeyColumn
    @DT(type = DT.Type.INTEGER)
    private int id;
    @Column
    @DuplicatedColumn
    @DT(type = DT.Type.SHORTTEXT)
    private String username;
    @Column
    @DuplicatedColumn
    @DT(type = DT.Type.SHORTTEXT)
    private String password;
    @Column
    @DuplicatedColumn
    @DT(type = DT.Type.SHORTTEXT)
    private String permission;

    public User(int id, String name) {
        this.id = id;
        this.username = name;
    }

    public User() {
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String getSchemaName() {
        return "storehouse";
    }

    @Override
    public String getTableName() {
        return "user";
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", permission='" + permission + '\'' +
                '}';
    }

    @Override
    public int getVersion() {
        return 1;
    }
}
