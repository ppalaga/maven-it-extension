package org.apache.maven.jupiter.extension.maven;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.List;

/**
 * @author Karl Heinz Marbaise
 */
public class Projects {

  //FIXME: Need to use a real tree structure
  // to support real project reactors with different levels etc.
  private List<MavenProjectResult> projects;

  public Projects(List<MavenProjectResult> projects) {
    this.projects = projects;
  }

  public List<MavenProjectResult> getProjects() {
    return projects;
  }
}
