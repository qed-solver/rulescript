package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyZeroCardinalityAntiJoin(t *testing.T) {
	rule := loadRule(t, "SimplifyZeroCardinalityAntiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	rc := e.f.Metadata().AddColumn("one", types.Int)
	oneRow := e.intValues([]opt.ColumnID{rc}, []int{0})
	e.expectRewrite(rule, e.f.ConstructAntiJoin(l, oneRow, nil, &memo.JoinPrivate{}), e.empty(lc[0]))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, e.intValues([]opt.ColumnID{rc}, []int{0}, []int{1}), nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, e.intValues([]opt.ColumnID{rc}, []int{1}), nil, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, oneRow, nil, &memo.JoinPrivate{}))
}
