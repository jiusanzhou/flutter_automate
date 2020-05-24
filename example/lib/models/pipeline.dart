

import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_automate_example/components/schema.dart';
import 'package:yaml/yaml.dart';

class Pipeline {
  String id;
  String name;
  String description;
  String icon;
  String path; // 代码路径，可能为相对，也可能是绝对
  String code; // 代码内容, 也有可能是直接有代码内容
  String main; // 函数入口
  String app; // 要操作的app

  String fullPath; // 代码全路径
  dynamic raw;

  // Configuration schema
  Schema paramSchema;

  Pipeline.fromJson(Map<String, dynamic> data) {
    raw = data;

    if (data == null) return;

    id = data["id"]; // ID
    name = data["name"] ?? "无名称"; // 名称
    description = data["description"] ?? name; // 描述
    app = data["app"]; // 目标app 还可以用 acitivity
    icon = data["icon"]; // 图标
    path = data["path"]; // 代码路径
    code = data["code"]; // 代码内容
    main = data["main"] ?? "main"; // 入口函数
    paramSchema = Schema.fromJson(data["params"]); // 参数描述

  }

  String withParams(dynamic data) {
    var params = json.encode(data);
    return """
      $code
      
      // 执行入口函数
      $main($params);
    """;
  }
}

class PipelineRemoteProvider {
  static PipelineRemoteProvider instance;

  // 直接实现 http 是否缓存???加密缓存???现在直接放内存

  String repo;
  String indexFile; // 尝试???
  bool isRemote = false;

  // 仓库名称
  String name;
  String index;

  List<Pipeline> pipelines = [];

  // 缓存 cache

  bool busying = true;

  PipelineRemoteProvider(this.repo) {
    instance = this;
  }

  Future<bool> init() async {

    Map<String, dynamic> data = {};

    isRemote = checkIsRemote(repo);

    // TODO: try to get index
    indexFile = repo + "/index.yaml";

    // 从远程和本地加载
    if (!isRemote) {
      data = await rootBundle.loadString(indexFile).then((data) {
        return json.decode(json.encode(loadYaml(data)));
      });
    } else {
      data = await Dio().get<String>(indexFile).then((res) {
        if (res.statusCode != 200) Future.error(res.statusMessage);
        return json.decode(json.encode(loadYaml(res.data)));
      });
    }

    name = data["name"] ?? "自动工具箱";

    // 保存
    (data["pipelines"] as List<dynamic>).forEach((e) {
      // 加载到 pipelines
      var p = Pipeline.fromJson(e);

      if (p.id == null) {
        print("pipeline 数据错误: ${p.raw}");
        return;
      }

      // 设置好代码全路径
      if (p.path != null) {
        if (isRemote) {
          if (checkIsRemote(p.path)) {
            p.fullPath = p.path;
          } else {
            p.fullPath = repo + "/" + p.path;
          }

          // TODO: 加载内容
          Dio().get<String>(p.fullPath).then((data) {
            if (data.statusCode == 200) {
              p.code = data.data;
            } else {
              print("加载 ${p.id} 失败: ${data.statusMessage}");
            }
          });
        } else {
          // 本地
          if (p.path.startsWith("/")) {
            p.fullPath = p.path;
          } else {
            p.fullPath = repo + "/" + p.path;
          }

          // TODO: 加载内容
          rootBundle.loadString(p.fullPath).then((data) {
            p.code = data;
          });
        }
      }

      // 保存
      pipelines.add(p);
    });

    busying = false;

    return true;
  }

  bool checkIsRemote(String path) {
    return path.startsWith("http://") || path.startsWith("https://");
  }
}