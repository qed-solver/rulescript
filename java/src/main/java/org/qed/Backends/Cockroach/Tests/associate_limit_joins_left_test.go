package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestAssociateLimitJoinsLeft(t *testing.T) {
	rule := loadRule(t, "AssociateLimitJoinsLeft")
	e := newEnv(t)
	a, aCols := e.scan("t", 1)
	b, bCols := e.scan("u", 1)
	c, cCols := e.scan("nn", 1)

	pab := e.filters(e.f.ConstructEq(e.v(aCols[0]), e.v(bCols[0])))
	pac := e.filters(e.f.ConstructEq(e.v(aCols[0]), e.v(cCols[0])))
	pbc := e.filters(e.f.ConstructEq(e.v(bCols[0]), e.v(cCols[0])))

	ab := e.f.ConstructLeftJoin(a, b, pab, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(ab, c, pac, &memo.JoinPrivate{})

	ac := e.f.ConstructInnerJoin(a, c, pac, &memo.JoinPrivate{})
	acb := e.f.ConstructLeftJoin(ac, b, pab, &memo.JoinPrivate{})
	want := e.f.ConstructProject(acb, nil, opt.MakeColSet(aCols[0], bCols[0], cCols[0]))
	e.expectRewrite(rule, before, want)

	innerAB := e.f.ConstructInnerJoin(a, b, pab, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(innerAB, c, pac, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab, c, pbc, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(ab, c, pac, &memo.JoinPrivate{}))
}
