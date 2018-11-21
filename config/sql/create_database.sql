DROP DATABASE IF EXISTS gpconnect;
CREATE DATABASE gpconnect DEFAULT CHARACTER SET utf8;

/* Delete the user (grant all to workaround MySQL not supporting 'IF EXISTS' for users) */
GRANT ALL ON gpconnect.* TO 'gpconnectdbuser' IDENTIFIED BY 'gpc0nn3ct';
DROP USER 'gpconnectdbuser';
FLUSH PRIVILEGES;

/* Create a new user with full privileges */
CREATE USER 'gpconnectdbuser' IDENTIFIED BY 'gpc0nn3ct';
GRANT ALL ON gpconnect.* TO 'gpconnectdbuser'@'%' IDENTIFIED BY 'gpc0nn3ct';
GRANT ALL ON gpconnect.* TO 'gpconnectdbuser'@'localhost' IDENTIFIED BY 'gpc0nn3ct';
FLUSH PRIVILEGES;