package com.lym.test;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {  // ✅ 独立类，不需要 static
    private String username;
    private String password;
}