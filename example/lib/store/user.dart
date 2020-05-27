


import 'package:flutter/material.dart';
import 'package:flutter_automate_example/models/user.dart';
import 'package:flutter_automate_example/utils/storage.dart';

class UserModel extends ChangeNotifier {

  static const String key = "key_user";

  User _user;

  User get user => _user;

  bool get hasUser => _user != null;

  UserModel() {
    var userMap = StorageManager.localStorage.getItem(key);
    _user = userMap != null ? User.fromJson(userMap) : null;
  }

  saveUser(User user) {
    _user = user;
    notifyListeners();
    StorageManager.localStorage.setItem(key, user);
  }

  removeUser() {
    _user = null;
    notifyListeners();
    StorageManager.localStorage.deleteItem(key);
  }
}