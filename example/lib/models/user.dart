class User {
  String username;
  String password;
  String token;

  User.fromJson(Map<String, dynamic> data) {
    username = data["username"];
    password = data["password"];
    token = data["token"];
  }
}