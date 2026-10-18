package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRewritePredicateSubquery(t *testing.T) {
	rule := loadRule(t, "RewritePredicateSubquery")
	e := newEnv(t)

	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	correlated := e.f.ConstructSelect(right, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))))
	existsCol := e.f.Metadata().AddColumn("exists", types.Bool)
	before := e.f.ConstructSelect(left, e.filters(e.f.ConstructExists(correlated, &memo.ExistsPrivate{LazyEvalProjectionCol: existsCol})))
	want := e.f.ConstructSemiJoin(left, right, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	left2, _ := e.scan("t", 1)
	right2, rightCols2 := e.scan("u", 1)
	uncorrelated := e.f.ConstructSelect(right2, e.filters(e.f.ConstructEq(e.v(rightCols2[0]), e.num(1))))
	existsCol2 := e.f.Metadata().AddColumn("exists", types.Bool)
	nearMiss := e.f.ConstructSelect(left2, e.filters(e.f.ConstructExists(uncorrelated, &memo.ExistsPrivate{LazyEvalProjectionCol: existsCol2})))
	e.expectNoMatch(rule, nearMiss)
}
