package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNotNull")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	nullBool := e.f.ConstructNull(types.Bool)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(nullBool))), e.f.ConstructSelect(s, e.filters(nullBool)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructTrue()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructNot(e.f.ConstructEq(e.v(cols[0]), e.num(1))))))
}

func TestSQLFoldNotNull(t *testing.T) {
	expectSQLFires(t, "FoldNotNull", "SELECT x FROM u WHERE NOT NULL")
}
