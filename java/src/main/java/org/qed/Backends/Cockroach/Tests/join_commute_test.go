package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinCommute(t *testing.T) {
	rule := loadRule(t, "JoinCommute")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	commuted := e.f.ConstructInnerJoin(r, l, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(commuted, memo.ProjectionsExpr{}, opt.MakeColSet(lc[0], rc[0]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
}
