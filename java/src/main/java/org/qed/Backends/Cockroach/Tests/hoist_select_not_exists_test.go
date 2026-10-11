package rulescript_test

import (
	"testing"
)

func TestHoistSelectNotExists(t *testing.T) { testHoistSelectExists(t, "HoistSelectNotExists", true) }

func TestSQLHoistSelectNotExists(t *testing.T) {
	expectSQLFires(t, "HoistSelectNotExists", "SELECT x FROM u WHERE NOT EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0) AND x>0")
}
