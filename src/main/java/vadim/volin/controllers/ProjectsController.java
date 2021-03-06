package vadim.volin.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vadim.volin.model.Project;
import vadim.volin.model.ProjectFile;
import vadim.volin.model.User;
import vadim.volin.services.ProjectFileService;
import vadim.volin.services.ProjectService;
import vadim.volin.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Controller
@SessionAttributes({"user"})
public class ProjectsController {

    @Autowired
    private UserService userService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectFileService projectFileService;

    @GetMapping("/projects")
    public String initPage(@ModelAttribute User user, Model model) {
        if (user.getRoles() == null || user == null || !user.getRoles().contains("ROLE_USER")) {
            return "redirect:/login";
        }
        user = userService.getByUserMail(user.getUsermail());
        model.addAttribute("pageName", "Projects");
        model.addAttribute("user", user);
        return "projects";
    }

    @PostMapping(value = "/projects/add", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<?> addProject(@RequestParam(name = "project_name", defaultValue = "") String project_name, @ModelAttribute User user, Model model) {
        if (user == null || project_name == null || project_name.equals("")) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        Project project = new Project(project_name);
        user.getProjects().add(project);
        User update = userService.editUser(user);
        project = update.getProjects().stream().filter(project1 -> project1.getProject_name().equals(project_name)).findFirst().get();
        model.addAttribute("user", update);
        return new ResponseEntity(String.valueOf(project.getProject_id()), HttpStatus.OK);
    }

    @GetMapping(value = "/projects/{user_id}/{project_id}")
    public String loadPersonalProjectPage(@PathVariable String user_id, @PathVariable String project_id, @ModelAttribute User user, Model model) {
        if (user == null || user.getRoles() == null || !user.getRoles().contains("ROLE_USER")
                || user_id == null) {
            return "redirect:/login";
        }
        if (project_id == null) {
            return "redirect:/projects";
        }
        user = userService.getByUserMail(user.getUsermail());
        boolean hasProject = false;
        Project project = null;
        List<Project> projectList = user.getProjects();
        for (int i = 0; i < projectList.size(); i++) {
            if (projectList.get(i).getProject_id().equals(Integer.parseInt(project_id))) {
                hasProject = true;
                project = projectList.get(i);
                model.addAttribute("pageName", project.getProject_name());
                model.addAttribute("project", project);
                model.addAttribute("user", user);
                break;
            }
        }
        if (hasProject) {
            return "project-info";
        } else {
            return "redirect:/projects";
        }
    }

    @PostMapping(value = "/projects/remove", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<?> removeProject(
            @RequestParam(name = "projectId", defaultValue = "") String projectId, @ModelAttribute User user, Model model
    ) {
        if (user == null || projectId == null || projectId.equals("")) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        Project project = null;
        for (int i = 0; i < user.getProjects().size(); i++) {
            if (user.getProjects().get(i).getProject_id().equals(Integer.parseInt(projectId))) {
                project = user.getProjects().get(i);
                user.getProjects().remove(project);
                User update = userService.editUser(user);
                if (project.getTeam().isEmpty()) {
                    projectService.removeProject(project);
                }
                model.addAttribute("user", update);
                break;
            }
        }
        return new ResponseEntity("Deleted!", HttpStatus.OK);
    }

    @PostMapping(value = "/projects/{id}/add/user", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<String> addUserToProject(
            @RequestParam(name = "membermail", defaultValue = "") String membermail, @PathVariable String id, @ModelAttribute User user, Model model
    ) {
        if (user == null || membermail == null || membermail.equals("")) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        User futureUser = userService.getByUserMail(membermail);
        if (futureUser == null) {
            return ResponseEntity.badRequest().body("User not found");
        }
        for (int i = 0; i < user.getProjects().size(); i++) {
            if (user.getProjects().get(i).getProject_id().equals(Integer.parseInt(id))) {
                if (user.getProjects().get(i).getTeam().contains(futureUser)
                        && futureUser.getProjects().contains(user.getProjects().get(i))) {
                    return ResponseEntity.badRequest().body("User already added!");
                } else {
                    user.getProjects().get(i).getTeam().add(futureUser);
                    futureUser.getProjects().add(user.getProjects().get(i));
                    futureUser = userService.editUser(futureUser);
                    projectService.editProject(user.getProjects().get(i));
                    model.addAttribute("user", user);
                    return ResponseEntity.ok("User added!");
                }
            }
        }
        return ResponseEntity.badRequest().body("Try later!");
    }

    @PostMapping("/projects/{id}/upload/file")
    @ResponseBody
    public ResponseEntity<?> uploadProjectFile(
            @RequestBody MultipartFile file, @PathVariable String id, @ModelAttribute User user, Model model
    ) {
        if (user.getRoles() == null || user == null || !user.getRoles().contains("ROLE_USER")) {
            return ResponseEntity.badRequest().body("Try later");
        }
        if (file.isEmpty()) {
            model.addAttribute("message", "Please, choose file!");
            return ResponseEntity.badRequest().body("Please, select image for uploading!");
        }
        try {
            Project project = user.getProjects().stream()
                    .filter(project1 -> (
                            project1.getProject_id().equals(Integer.valueOf(id))
                    ))
                    .findFirst()
                    .get();

            byte[] bytes = file.getBytes();
            Path path = Paths.get(
                    "/home/vadim/Documents/Spring/springMVC-courses/target/springMVC-courses/manluck_data/projects/"
//                    "/manluck_data/projects/"
                            + id + "/"
                            + file.getOriginalFilename()
            );
            Files.createDirectories(path.getParent());

            Files.write(path, bytes);
            ProjectFile projectFile = new ProjectFile("/manluck_data/projects/" + id + "/" + file.getOriginalFilename());
            if (project.getProjectFiles().contains(projectFile)) {
                return ResponseEntity.badRequest().body("File already upload");
            } else {
                project.getProjectFiles().add(projectFile);
                projectFile.setProject(project);
                projectFile = projectFileService.addProjectFile(projectFile);
                model.addAttribute("user", user);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Please, select file for uploading!");
        }
        return ResponseEntity.ok("Successfully upload!");
    }

    @PostMapping(value = "/projects/remove/user", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<String> removeUserFromProject(
            @RequestParam(name = "projectId", defaultValue = "") String projectId, @RequestParam(name = "usermail", defaultValue = "") String usermail, @ModelAttribute User user, Model model
    ) {
        if (user == null || projectId == null || projectId.equals("") || usermail == null || usermail.equals("")) {
            return ResponseEntity.badRequest().body("Try later");
        }
        for (int i = 0; i < user.getProjects().size(); i++) {
            if (user.getProjects().get(i).getProject_id().equals(Integer.parseInt(projectId))) {
                User removedUser = userService.getByUserMail(usermail);
                if (removedUser == null) {
                    return ResponseEntity.badRequest().body("User not found!");
                }

                boolean removeProject = false;
                for (int j = 0; j < removedUser.getProjects().size(); j++) {
                    if (removedUser.getProjects().get(j).getProject_id().equals(Integer.parseInt(projectId))) {
                        removeProject = removedUser.getProjects().remove(removedUser.getProjects().get(j));
                        userService.editUser(removedUser);
                    }
                }
                boolean removeUser = false;
                for (int j = 0; j < user.getProjects().get(i).getTeam().size(); j++) {
                    if (user.getProjects().get(i).getTeam().get(j).equals(removedUser)) {
                        removeUser = user.getProjects().get(i).getTeam().remove(removedUser);
                        projectService.editProject(user.getProjects().get(i));
                    }
                }
                if (user.getProjects().get(i).getTeam().isEmpty()) {
                    projectService.removeProject(user.getProjects().get(i));
                }
                model.addAttribute("user", user);
                if (removeUser & removeProject) {
                    return ResponseEntity.ok("Deleted!");
                }
            }
        }
        return ResponseEntity.badRequest().body("Try again");
    }


    @GetMapping(value = "/tasks/{project_id}")
    public String initPrjTasksPage(@PathVariable String project_id, @ModelAttribute User user, Model model) {
        if (user.getRoles() == null || user == null || !user.getRoles().contains("ROLE_USER")) {
            return "redirect:/login";
        }
        if (project_id == null || project_id.equals("")) {
            return "redirect:/projects";
        }

        user = userService.getByUserMail(user.getUsermail());
        for (int i = 0; i < user.getProjects().size(); i++) {
            if (user.getProjects().get(i).getProject_id().equals(Integer.parseInt(project_id))) {
                model.addAttribute("pageName", "" + user.getProjects().get(i).getProject_name() + "-tasks");
                model.addAttribute("project", user.getProjects().get(i));
                model.addAttribute("user", user);
                return "project-tasks";
            }
        }
        model.addAttribute("user", user);
        return "redirect:/projects";
    }

    //    TODO: sync updates tasks for team members;
    @PostMapping(value = "/projects/{project_id}/save/tasks", produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public ResponseEntity<String> saveProjectTasks(@RequestBody String jsonTasks, @PathVariable String project_id, @ModelAttribute User user, Model model) {
        if (user.getRoles() == null || user == null || !user.getRoles().contains("ROLE_USER")) {
            return ResponseEntity.badRequest().body("Try later");
        }

        for (int i = 0; i < user.getProjects().size(); i++) {
            if (user.getProjects().get(i).getProject_id().equals(Integer.parseInt(project_id))) {
                model.addAttribute("pageName", "" + user.getProjects().get(i).getProject_name() + "-tasks");
                if (jsonTasks != null) {
                    if (!jsonTasks.equals(user.getProjects().get(i).getProject_tasks())) {
                        user.getProjects().get(i).setProject_tasks(jsonTasks);
                        projectService.editProject(user.getProjects().get(i));
                        user = userService.getByUserMail(user.getUsermail());
                        model.addAttribute("user", user);
                        return ResponseEntity.ok("Update!");
                    }
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body("Updated!");
                }
            }
        }
        model.addAttribute("user", user);
        return ResponseEntity.badRequest().body("Try later");
    }


}
