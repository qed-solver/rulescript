package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestEliminateRedundantBarrier(t *testing.T) {
	rule := loadRule(t, "EliminateRedundantBarrier")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	inner := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(inner, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, inner)

	e.expectNoMatch(rule, inner)
	e.expectNoMatch(rule, e.f.ConstructProject(inner, nil, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0)))))
}

func TestSQLEliminateRedundantBarrier(t *testing.T) {
	expectSQLFiresOrNot(t, "EliminateRedundantBarrier", "SELECT a FROM (SELECT a FROM (VALUES (1),(2)) AS v(a)) AS q", false)
}
