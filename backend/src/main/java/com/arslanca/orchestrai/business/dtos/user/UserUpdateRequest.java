package com.arslanca.orchestrai.business.dtos.user;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record UserUpdateRequest(
    @NotBlank(message = "Username cannot be blank")
    @Size(max = 100, message = "Kullanıcı adı en fazla 100 karakter olabilir") String username,
    @Email(message = "Geçerli bir email adresi giriniz")
    @Size(max = 255, message = "Email en fazla 255 karakter olabilir") String email
) {}
