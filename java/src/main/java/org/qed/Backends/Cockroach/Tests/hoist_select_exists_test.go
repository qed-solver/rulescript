package rulescript_test

import (
	"testing"
)

func TestHoistSelectExists(t *testing.T) { testHoistSelectExists(t, "HoistSelectExists", false) }

func TestSQLHoistSelectExists(t *testing.T) {
	expectSQLFires(t, "HoistSelectExists", "SELECT x FROM u WHERE EXISTS (SELECT * FROM (VALUES (1),(2)) AS s(a) WHERE a>0) AND x>0")
}
