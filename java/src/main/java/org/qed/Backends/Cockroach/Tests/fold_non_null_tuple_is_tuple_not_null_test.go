package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFoldNonNullTupleIsTupleNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullTupleIsTupleNotNull")
	e := newEnv(t)
	s, cols := e.scan("nn", 2)
	notNullA := e.f.ConstructIsNot(e.v(cols[0]), memo.NullSingleton)
	notNullB := e.f.ConstructIsNot(e.v(cols[1]), memo.NullSingleton)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(notNullA, notNullB)), s)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(notNullB, notNullA)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(notNullA)))
}

func TestSQLFoldNonNullTupleIsTupleNotNull(t *testing.T) {
	expectSQLFires(t, "FoldNonNullTupleIsTupleNotNull", "SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a IS NOT NULL AND b IS NOT NULL")
}
